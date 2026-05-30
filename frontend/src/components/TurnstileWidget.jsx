import React, { useEffect, useRef } from 'react';

const SITE_KEY = '0x4AAAAAADaLDGUynCqqXN06';

export default function TurnstileWidget({ onToken }) {
    const containerRef = useRef(null);
    const widgetIdRef = useRef(null);

    useEffect(() => {
        let cancelled = false;

        const tryRender = () => {
            if (cancelled || !containerRef.current || !window.turnstile) return false;
            if (widgetIdRef.current != null) return true;
            widgetIdRef.current = window.turnstile.render(containerRef.current, {
                sitekey: SITE_KEY,
                theme: 'dark',
                callback: (token) => onToken(token),
                'expired-callback': () => onToken(''),
                'error-callback': () => onToken('')
            });
            return true;
        };

        let interval = null;
        if (!tryRender()) {
            interval = setInterval(() => { if (tryRender()) clearInterval(interval); }, 200);
        }

        return () => {
            cancelled = true;
            if (interval) clearInterval(interval);
            if (widgetIdRef.current != null && window.turnstile) {
                try { window.turnstile.remove(widgetIdRef.current); } catch (e) { /* noop */ }
            }
        };
    }, []);

    return <div ref={containerRef} className="flex justify-center min-h-[65px]" />;
}
