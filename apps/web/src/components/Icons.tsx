// Small outline icon set (24px grid, 1.8 stroke) so the page needs no icon font.
const paths: Record<string, string> = {
  shield: "M12 3l7 3v5c0 4.6-3 8.6-7 10-4-1.4-7-5.4-7-10V6l7-3z",
  shieldCheck: "M12 3l7 3v5c0 4.6-3 8.6-7 10-4-1.4-7-5.4-7-10V6l7-3z M9 12l2 2 4-4",
  link: "M10 14a4 4 0 005.7 0l3-3a4 4 0 00-5.7-5.7l-1 1 M14 10a4 4 0 00-5.7 0l-3 3a4 4 0 005.7 5.7l1-1",
  book: "M4 5a2 2 0 012-2h5v17H6a2 2 0 01-2-2V5z M20 5a2 2 0 00-2-2h-5v17h5a2 2 0 002-2V5z",
  info: "M12 21a9 9 0 100-18 9 9 0 000 18z M12 11v5 M12 8h.01",
  paste: "M9 4h6v3H9z M8 5H6a1 1 0 00-1 1v14a1 1 0 001 1h12a1 1 0 001-1V6a1 1 0 00-1-1h-2",
  image: "M4 5h16v14H4z M4 16l5-5 4 4 3-3 4 4 M15 9h.01",
  search: "M11 18a7 7 0 100-14 7 7 0 000 14z M20 20l-4-4",
  refresh: "M4 12a8 8 0 0114-5.3L20 9 M20 4v5h-5 M20 12a8 8 0 01-14 5.3L4 15 M4 20v-5h5",
  warning: "M12 4l9 16H3L12 4z M12 10v4 M12 17h.01",
  check: "M12 21a9 9 0 100-18 9 9 0 000 18z M8 12l3 3 5-6",
  question: "M12 21a9 9 0 100-18 9 9 0 000 18z M9.5 9.5a2.5 2.5 0 114 2c-.9.6-1.5 1.2-1.5 2.5 M12 17h.01",
  megaphone: "M4 10v4h3l7 4V6L7 10H4z M18 9a4 4 0 010 6",
  lock: "M6 11h12v9H6z M8 11V8a4 4 0 018 0v3",
  chevron: "M6 9l6 6 6-6",
  x: "M6 6l12 12 M18 6L6 18",
};

export function Icon({ name, className = "size-5" }: { name: keyof typeof paths | string; className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden>
      {(paths[name] ?? "").split(" M").map((d, i) => <path key={i} d={i ? "M" + d : d} />)}
    </svg>
  );
}
