import '@testing-library/jest-dom/vitest'

// jsdom lacks matchMedia, which Mantine's color scheme handling needs.
window.matchMedia ??= (() => ({ matches: false, addEventListener() {}, removeEventListener() {} })) as never
