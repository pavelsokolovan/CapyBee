import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        honey: {
          50: '#fff9eb',
          100: '#fff1c7',
          200: '#ffe08a',
          300: '#ffd04f',
          400: '#f7b927',
          500: '#dda11a',
          600: '#b67d14',
          700: '#855a11',
          800: '#5b3d13',
          900: '#38270f'
        }
      }
    }
  },
  plugins: []
} satisfies Config;
