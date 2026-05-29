/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                chessBg: '#161512',
                panelBg: '#1e1c18',
                borderColor: '#2a2824',
                textDark: '#bababa',
                textBright: '#fff',
                accentColor: '#577d36',
                accentHover: '#689243',
                dangerColor: '#dc3545',
            }
        },
    },
    plugins: [],
}