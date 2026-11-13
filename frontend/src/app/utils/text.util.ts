/** "licensePlate" → "License plate": turns a camelCase key into a label for display. */
export function humanizeKey(key: string): string {
  const words = key.replace(/([A-Z])/g, ' $1').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}
