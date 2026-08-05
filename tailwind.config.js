/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/main/webapp/WEB-INF/jsp/**/*.jsp",
    "./src/main/webapp/WEB-INF/jsp/**/*.jspf"
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ["Open Sans", "Helvetica", "Arial", "sans-serif"],
        display: ["Open Sans", "Helvetica", "Arial", "sans-serif"]
      },
      colors: {
        sacco: {
          brown: "#8A4B24",
          blue: "#2F348D",
          green: "#3F9C4B",
          cream: "#F7F4EE",
          ink: "#172033"
        }
      },
      boxShadow: {
        frame: "0 18px 45px -22px rgba(23, 32, 51, 0.32)"
      }
    }
  },
  plugins: []
};
