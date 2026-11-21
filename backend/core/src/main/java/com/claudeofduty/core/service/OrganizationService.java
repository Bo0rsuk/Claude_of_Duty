package com.claudeofduty.core.service;

import com.claudeofduty.core.api.ApiException;
import com.claudeofduty.core.api.Caller;
import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.Member.Role;
import com.claudeofduty.core.model.Organization;
import com.claudeofduty.core.port.Membership;
import com.claudeofduty.core.port.UserDirectory;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Organizations, invite codes and roles. Accounts live in Cognito; a user
 * becomes useful only after creating an organization or joining one by code.
 */
public final class OrganizationService {

    /** Without characters that are easy to confuse when read aloud or typed: 0/O, 1/I/L. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    public record OrganizationSummary(String orgId, String name, Role role) {
    }

    /**
     * @param tokenRefreshNeeded true when the user's token does not carry their current organization. The
     *                           client must get a new token before subscribing to real-time channels.
     */
    public record Profile(String userId, String email, String name, String phone, OrganizationSummary org,
            boolean tokenRefreshNeeded) {
    }

    private final Membership membership;
    private final UserDirectory users;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrganizationService(Membership membership, UserDirectory users, Clock clock) {
        this.membership = membership;
        this.users = users;
        this.clock = clock;
    }

    public Profile profile(Caller caller) {
        Member member = caller.member();
        if (member == null) {
            return new Profile(caller.userId(), caller.email(), null, null, null, false);
        }
        boolean tokenOutOfDate = !member.orgId().equals(caller.tokenOrgId());
        if (tokenOutOfDate) {
            // Also repairs accounts that joined before the organization was recorded with the sign-in system.
            users.setOrganization(caller.userId(), member.orgId());
        }
        String orgName = membership.findOrganization(member.orgId()).map(Organization::name).orElse(null);
        return new Profile(caller.userId(), member.email(), member.name(), member.phone(),
                new OrganizationSummary(member.orgId(), orgName, member.role()), tokenOutOfDate);
    }

    /** Name and phone are what notifications use to reach the user. */
    public Profile updateProfile(Caller caller, String name, String phone) {
        caller.orgId();
        requirePhone(phone);
        Member current = caller.member();
        Member updated = new Member(current.orgId(), current.userId(), current.role(),
                name != null ? name : current.name(), current.email(),
                phone == null ? current.phone() : phone.isBlank() ? null : phone.strip(), current.joinedAt());
        membership.saveMember(updated);
        return profile(new Caller(caller.userId(), caller.email(), updated, caller.tokenOrgId()));
    }

    public Organization create(Caller caller, String name) {
        requireNoOrganization(caller);
        if (name == null || name.isBlank()) {
            throw ApiException.invalid("An organization needs a name");
        }
        Organization organization =
                new Organization(UUID.randomUUID().toString(), name.strip(), newInviteCode(), clock.instant());
        membership.saveOrganization(organization);
        membership.saveMember(newMember(caller, organization, Role.ADMIN));
        users.setOrganization(caller.userId(), organization.orgId());
        return organization;
    }

    /** Whoever joins an organization that has no members yet becomes its admin. Everyone after joins as a user. */
    public Organization join(Caller caller, String inviteCode) {
        requireNoOrganization(caller);
        Organization organization = membership
                .findOrganizationByInviteCode(inviteCode == null ? "" : inviteCode.strip().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Unknown invite code"));
        Role role = membership.members(organization.orgId()).isEmpty() ? Role.ADMIN : Role.USER;
        membership.saveMember(newMember(caller, organization, role));
        users.setOrganization(caller.userId(), organization.orgId());
        return withoutInviteCode(organization);
    }

    /** The invite code is shown to admins only. */
    public Organization organization(Caller caller) {
        Organization organization = membership.findOrganization(caller.orgId())
                .orElseThrow(() -> ApiException.notFound("Organization does not exist"));
        return caller.has(Role.ADMIN) ? organization : withoutInviteCode(organization);
    }

    public Organization regenerateInviteCode(Caller caller) {
        Organization current = organization(caller);
        Organization updated = new Organization(current.orgId(), current.name(), newInviteCode(), current.createdAt());
        membership.saveOrganization(updated);
        return updated;
    }

    public List<Member> members(Caller caller) {
        return membership.members(caller.orgId());
    }

    /**
     * An admin adds a person by email. The account is created for them, already
     * in this organization, and they receive a temporary password by email.
     */
    public Member invite(Caller caller, String email, String name, Role role) {
        String orgId = caller.orgId();
        String address = email == null ? "" : email.strip().toLowerCase();
        if (!address.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw ApiException.invalid("email must be an email address");
        }
        if (membership.members(orgId).stream().anyMatch(member -> address.equalsIgnoreCase(member.email()))) {
            throw ApiException.conflict(address + " is already a member");
        }
        String userId = users.createUser(address, orgId);
        Member member = new Member(orgId, userId, role == null ? Role.USER : role,
                name == null || name.isBlank() ? null : name.strip(), address, null, clock.instant());
        membership.saveMember(member);
        return member;
    }

    /**
     * An admin changes a member's role, name or phone. A part that is null stays as it is;
     * a blank phone removes the number.
     */
    public Member updateMember(Caller caller, String userId, Role role, String name, String phone) {
        if (role == null && name == null && phone == null) {
            throw ApiException.invalid("Send a role (user, team_lead or admin), a name or a phone to change");
        }
        requirePhone(phone);
        Member member = requireMember(caller, userId);
        if (role != null && member.role() == Role.ADMIN && role != Role.ADMIN) {
            requireAnotherAdmin(caller, userId);
        }
        Member updated = new Member(member.orgId(), member.userId(), role != null ? role : member.role(),
                name == null ? member.name() : name.isBlank() ? null : name.strip(), member.email(),
                phone == null ? member.phone() : phone.isBlank() ? null : phone.strip(), member.joinedAt());
        membership.saveMember(updated);
        return updated;
    }

    private static void requirePhone(String phone) {
        if (phone != null && !phone.isBlank() && !phone.strip().matches("\\+[0-9]{8,15}")) {
            throw ApiException.invalid("phone must be in the international form, for example +48600100200");
        }
    }

    public void remove(Caller caller, String userId) {
        Member member = requireMember(caller, userId);
        if (member.role() == Role.ADMIN) {
            requireAnotherAdmin(caller, userId);
        }
        membership.removeMember(member.orgId(), userId);
        users.setOrganization(userId, null);
    }

    private Member requireMember(Caller caller, String userId) {
        return membership.members(caller.orgId()).stream()
                .filter(member -> member.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("No member " + userId + " in this organization"));
    }

    /** An organization must never be left without an admin. */
    private void requireAnotherAdmin(Caller caller, String userId) {
        boolean anotherAdmin = membership.members(caller.orgId()).stream()
                .anyMatch(member -> member.role() == Role.ADMIN && !member.userId().equals(userId));
        if (!anotherAdmin) {
            throw ApiException.conflict("This is the organization's only admin");
        }
    }

    private static void requireNoOrganization(Caller caller) {
        if (caller.member() != null) {
            throw ApiException.conflict("You already belong to an organization");
        }
    }

    private Member newMember(Caller caller, Organization organization, Role role) {
        return new Member(organization.orgId(), caller.userId(), role, null, caller.email(), null, clock.instant());
    }

    private static Organization withoutInviteCode(Organization organization) {
        return new Organization(organization.orgId(), organization.name(), null, organization.createdAt());
    }

    private String newInviteCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                code.append('-');
            }
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
