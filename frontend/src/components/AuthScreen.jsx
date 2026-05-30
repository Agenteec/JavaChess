import React, { useState } from 'react';
import { ShieldAlert, CheckCircle2, Mail, ArrowLeft } from 'lucide-react';
import TurnstileWidget from './TurnstileWidget.jsx';

export default function AuthScreen({ userServiceUrl, onAuthSuccess }) {
    const [view, setView] = useState('login'); // 'login' | 'register' | 'forgot'
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [email, setEmail] = useState('');
    const [dangerAlert, setDangerAlert] = useState('');
    const [successAlert, setSuccessAlert] = useState('');
    const [showResendBtn, setShowResendBtn] = useState(false);
    const [busy, setBusy] = useState(false);
    const [captchaToken, setCaptchaToken] = useState('');
    const [captchaKey, setCaptchaKey] = useState(0);

    const resetCaptcha = () => { setCaptchaToken(''); setCaptchaKey(k => k + 1); };

    const reset = () => { setDangerAlert(''); setSuccessAlert(''); setShowResendBtn(false); };
    const switchView = (v) => { reset(); setView(v); };

    const post = async (endpoint, body) => {
        const res = await fetch(userServiceUrl + endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.message || 'Произошла ошибка. Попробуйте позже.');
        return data;
    };

    const handleAuthSubmit = async () => {
        reset();
        if (!username || !password) { setDangerAlert("Пожалуйста, заполните все поля."); return; }
        if (view === 'register' && !email) { setDangerAlert("Пожалуйста, укажите адрес электронной почты."); return; }
        if (view === 'register' && !captchaToken) { setDangerAlert("Подтвердите, что вы не робот."); return; }

        setBusy(true);
        try {
            const body = view === 'register' ? { username, password, email, captchaToken } : { username, password };
            const data = await post(view === 'login' ? '/login' : '/register', body);

            if (data.token == null) {
                setSuccessAlert("Письмо со ссылкой для подтверждения отправлено на вашу почту. Подтвердите аккаунт перед входом.");
                setShowResendBtn(true);
            } else {
                localStorage.setItem("jwt_token", data.token);
                localStorage.setItem("username", data.username);
                localStorage.setItem("rating", data.rating);
                onAuthSuccess(data.username, data.rating);
            }
        } catch (err) {
            setDangerAlert(err.message);
            if (err.message.includes("подтверд")) setShowResendBtn(true);
            if (view === 'register') resetCaptcha();
        } finally {
            setBusy(false);
        }
    };

    const handleResendEmail = async () => {
        reset();
        if (!username) { setDangerAlert("Введите имя пользователя, чтобы выслать письмо повторно."); return; }
        setBusy(true);
        try {
            const data = await post('/resend', { login: username });
            setSuccessAlert(data.message || "Письмо отправлено повторно.");
        } catch (err) {
            setDangerAlert(err.message);
        } finally {
            setBusy(false);
        }
    };

    const handleForgotSubmit = async () => {
        reset();
        if (!email) { setDangerAlert("Укажите адрес электронной почты."); return; }
        setBusy(true);
        try {
            const data = await post('/forgot-password', { email });
            setSuccessAlert(data.message);
        } catch (err) {
            setDangerAlert(err.message);
        } finally {
            setBusy(false);
        }
    };

    const inputCls = "bg-[#15130F] border border-[#322C24] text-white p-3 rounded focus:outline-none focus:border-[#6E9F4A]";

    return (
        <div className="flex flex-col gap-4 w-full max-w-[380px] mt-10">
            {view !== 'forgot' && (
                <div className="flex gap-2">
                    <button onClick={() => switchView('login')} className={`flex-1 p-3 font-bold rounded-lg cursor-pointer transition-colors ${view === 'login' ? 'bg-[#6E9F4A] text-white' : 'bg-[#1A1711] text-[#E9E5DD] hover:bg-[#2A251E]'}`}>Вход</button>
                    <button onClick={() => switchView('register')} className={`flex-1 p-3 font-bold rounded-lg cursor-pointer transition-colors ${view === 'register' ? 'bg-[#6E9F4A] text-white' : 'bg-[#1A1711] text-[#E9E5DD] hover:bg-[#2A251E]'}`}>Регистрация</button>
                </div>
            )}

            <div className="bg-[#201D17] border border-[#322C24] rounded-lg p-7 shadow-xl flex flex-col gap-4">
                <h2 className="text-xl font-bold text-white text-center">
                    {view === 'login' ? 'Войти в аккаунт' : view === 'register' ? 'Создать аккаунт' : 'Восстановление пароля'}
                </h2>

                {dangerAlert && <div className="bg-[#4a1c1c] border border-red-500 text-red-200 p-3 rounded text-sm text-center"><ShieldAlert className="inline w-4 h-4 mr-1"/> {dangerAlert}</div>}
                {successAlert && <div className="bg-[#1c3d24] border border-green-500 text-green-200 p-3 rounded text-sm text-center"><CheckCircle2 className="inline w-4 h-4 mr-1"/> {successAlert}</div>}

                {showResendBtn && (
                    <button onClick={handleResendEmail} disabled={busy} className="bg-[#1A1711] border border-[#E8C56A]/40 text-[#E8C56A] p-2.5 rounded font-bold text-sm hover:bg-[#2A251E] inline-flex items-center justify-center gap-1.5 disabled:opacity-50"><Mail className="w-4 h-4" /> Выслать письмо повторно</button>
                )}

                {view === 'forgot' ? (
                    <>
                        <p className="text-xs text-[#9A958C] text-center">Введите почту, указанную при регистрации — мы пришлём ссылку для сброса пароля.</p>
                        <div className="flex flex-col gap-1">
                            <label className="text-xs font-bold text-[#9A958C]">Электронная почта</label>
                            <input type="email" value={email} onChange={e => setEmail(e.target.value)} className={inputCls} placeholder="example@mail.com" />
                        </div>
                        <button onClick={handleForgotSubmit} disabled={busy} className="bg-[#6E9F4A] text-white p-3 rounded font-bold hover:bg-[#7DB356] mt-1 disabled:opacity-50">Отправить ссылку</button>
                        <button onClick={() => switchView('login')} className="text-xs text-[#9A958C] hover:text-[#E8C56A] inline-flex items-center justify-center gap-1"><ArrowLeft className="w-3 h-3" /> Назад ко входу</button>
                    </>
                ) : (
                    <>
                        <div className="flex flex-col gap-1">
                            <label className="text-xs font-bold text-[#9A958C]">Имя пользователя</label>
                            <input type="text" value={username} onChange={e => setUsername(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleAuthSubmit()} className={inputCls} placeholder="Например, player1" />
                        </div>

                        {view === 'register' && (
                            <div className="flex flex-col gap-1">
                                <label className="text-xs font-bold text-[#9A958C]">Электронная почта</label>
                                <input type="email" value={email} onChange={e => setEmail(e.target.value)} className={inputCls} placeholder="example@mail.com" />
                            </div>
                        )}

                        <div className="flex flex-col gap-1">
                            <label className="text-xs font-bold text-[#9A958C]">Пароль</label>
                            <input type="password" value={password} onChange={e => setPassword(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleAuthSubmit()} className={inputCls} placeholder="Минимум 6 символов" />
                        </div>

                        {view === 'register' && <TurnstileWidget key={captchaKey} onToken={setCaptchaToken} />}

                        <button onClick={handleAuthSubmit} disabled={busy} className="bg-[#6E9F4A] text-white p-3 rounded font-bold hover:bg-[#7DB356] mt-2 disabled:opacity-50">
                            {busy ? 'Подождите…' : view === 'login' ? 'Войти' : 'Зарегистрироваться'}
                        </button>

                        {view === 'login' && (
                            <button onClick={() => switchView('forgot')} className="text-xs text-[#9A958C] hover:text-[#E8C56A] -mt-1">Забыли пароль?</button>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}
