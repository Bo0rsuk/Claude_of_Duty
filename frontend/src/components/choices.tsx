export function Choices({
  items,
  values,
  onChange,
}: {
  items: string[];
  values: string[];
  onChange: (v: string[]) => void;
}) {
  return (
    <div className="choices">
      {items.map((item) => (
        <label key={item}>
          <input
            type="checkbox"
            checked={values.includes(item)}
            onChange={(e) =>
              onChange(
                e.target.checked
                  ? [...values, item]
                  : values.filter((x) => x !== item),
              )
            }
          />
          {item}
        </label>
      ))}
    </div>
  );
}
