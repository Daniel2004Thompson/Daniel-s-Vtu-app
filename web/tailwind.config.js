/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        vtu: {
          green: '#00875A',
          greenLight: '#E8F8F2',
          greenSecondary: '#00B074',
          greenDark: '#005A3C',
          navy: '#0A192F',
          navySurface: '#102A43',
          navyCard: '#1B3A5A',
          gold: '#F59E0B',
          cyan: '#06B6D4',
          purple: '#8B5CF6',
          bg: '#F7F9FC',
          border: '#E2E8F0',
        },
      },
      fontFamily: {
        sans: ['"Plus Jakarta Sans"', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
    },
  },
  plugins: [],
};
