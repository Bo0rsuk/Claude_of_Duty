/** "1 rule", "2 rules". */
export const plural = (count: number, noun: string) => count + " " + noun + (count === 1 ? "" : "s");
