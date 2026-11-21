package com.claudeofduty.core.port;

import com.claudeofduty.core.model.Member;
import com.claudeofduty.core.model.Organization;
import java.util.List;
import java.util.Optional;

/** Organizations and who belongs to them. A user belongs to at most one organization. */
public interface Membership {

    Optional<Member> findMember(String userId);

    List<Member> members(String orgId);

    void saveMember(Member member);

    void removeMember(String orgId, String userId);

    Optional<Organization> findOrganization(String orgId);

    Optional<Organization> findOrganizationByInviteCode(String inviteCode);

    void saveOrganization(Organization organization);
}
