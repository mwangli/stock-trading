/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        background: '#050505', // Deep Black
        surface: '#0a0c10',    // Card Background
        primary: '#00e396',    // Neon Green (Stock Up)
        secondary: '#ff4560',  // Neon Red (Stock Down)
        accent: '#775dd0',     // Neon Purple (Highlight)
        border: 'rgba(255, 255, 255, 0.1)',
      },
      fontFamily: {
        mono: ['"Fira Code"', 'monospace'], // For code/numbers
        sans: ['"Inter"', 'sans-serif'],    // For UI
      },
      animation: {
        'spin-slow': 'spin 3s linear infinite',
        'pulse-fast': 'pulse 1.5s cubic-bezier(0.4, 0, 0.6, 1) infinite',
      }
    },
  },
  plugins: [],
}
