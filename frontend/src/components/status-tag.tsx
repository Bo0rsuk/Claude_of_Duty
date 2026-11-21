export function StatusTag({ value }: { value: string }) {
  return (
    <span className={"status " + value.toLowerCase()}>
      <i />
      {value}
    </span>
  );
}
