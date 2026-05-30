import React, { useState } from 'react';
import { ShieldAlert, CheckCircle2, Mail } from 'lucide-react';

export default function AuthScreen({ userServiceUrl, onAuthSuccess }) {
    const [authTab, setAuthTab] = useState('login');
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [email, setEmail] = useState('');
    const [dangerAlert, setDangerAlert] = useState('');
    const [successAlert, setSuccessAlert] = useState('');
    const [showResendBtn, setShowResendBtn] = useState(false);

    const handleAuthSubmit = () => {
        setDangerAlert('');
        setSuccessAlert('');
        setShowResendBtn(false);

        if (!username || !password) {
            setDangerAlert("Пожалуйста, заполните все поля.");
            return;
        }
        if (authTab === 'register' && !email) {
            setDangerAlert("Пожалуйста, укажите адрес электронной почты.");
            return;
        }

        const endpoint = authTab === 'login' ? '/login' : '/register';
        const body = { username, password };
        if (authTab === 'register') body.email = email;

        fetch(userServiceUrl + endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        })
            .then(res => {
                if (!res.ok) throw new Error(authTab === 'login' ? "Неверный логин или пароль." : "Имя или почта уже заняты.");
                return res.json();
            })
            .then(data => {
                if (data.token === null) {
                    setSuccessAlert("Письмо со ссылкой для верификации отправлено на вашу почту. Пожалуйста, подтвердите аккаунт перед входом.");
                    setTimeout(() => {
                        setAuthTab('login');
                        setSuccessAlert('');
                    }, 5000);
                } else {
                    localStorage.setItem("jwt_token", data.token);
                    localStorage.setItem("username", data.username);
                    localStorage.setItem("rating", data.rating);
                    onAuthSuccess(data.username, data.rating);
                }
            })
            .catch(err => {
                setDangerAlert(err.message);
                if (err.message.includes("подтвердите")) setShowResendBtn(true);
            });
    };

    const handleResendEmail = () => {
        //todo Ресенд запилить нужно будет
    };

    return (
        <div className="flex flex-col gap-4 w-full max-w-[380px] mt-10">
            <div className="flex gap-2">
                <button onClick={() => { setAuthTab('login'); setDangerAlert(''); }} className={`flex-1 p-3 font-bold rounded-lg cursor-pointer transition-colors ${authTab === 'login' ? 'bg-[#577d36] text-white' : 'bg-[#262421] text-[#bababa] hover:bg-[#363431]'}`}>Вход</button>
                <button onClick={() => { setAuthTab('register'); setDangerAlert(''); }} className={`flex-1 p-3 font-bold rounded-lg cursor-pointer transition-colors ${authTab === 'register' ? 'bg-[#577d36] text-white' : 'bg-[#262421] text-[#bababa] hover:bg-[#363431]'}`}>Регистрация</button>
            </div>

            <div className="bg-[#1e1c18] border border-[#2a2824] rounded-lg p-7 shadow-xl flex flex-col gap-4">
                <h2 className="text-xl font-bold text-white text-center">{authTab === 'login' ? 'Войти в аккаунт' : 'Создать аккаунт'}</h2>

                {dangerAlert && <div className="bg-[#4a1c1c] border border-red-500 text-red-200 p-3 rounded text-sm text-center"><ShieldAlert className="inline w-4 h-4 mr-1"/> {dangerAlert}</div>}
                {successAlert && <div className="bg-[#1c3d24] border border-green-500 text-green-200 p-3 rounded text-sm text-center"><CheckCircle2 className="inline w-4 h-4 mr-1"/> {successAlert}</div>}

                <div className="flex flex-col gap-1">
                    <label className="text-xs font-bold text-[#8b8985]">Имя пользователя</label>
                    <input type="text" value={username} onChange={e => setUsername(e.target.value)} className="bg-[#161512] border border-[#2a2824] text-white p-3 rounded focus:outline-none focus:border-[#577d36]" placeholder="Например, player1" />
                </div>

                {authTab === 'register' && (
                    <div className="flex flex-col gap-1">
                        <label className="text-xs font-bold text-[#8b8985]">Электронная почта</label>
                        <input type="email" value={email} onChange={e => setEmail(e.target.value)} className="bg-[#161512] border border-[#2a2824] text-white p-3 rounded focus:outline-none focus:border-[#577d36]" placeholder="example@mail.com" />
                    </div>
                )}

                <div className="flex flex-col gap-1">
                    <label className="text-xs font-bold text-[#8b8985]">Пароль</label>
                    <input type="password" value={password} onChange={e => setPassword(e.target.value)} className="bg-[#161512] border border-[#2a2824] text-white p-3 rounded focus:outline-none focus:border-[#577d36]" placeholder="Введите ваш пароль" />
                </div>

                <button onClick={handleAuthSubmit} className="bg-[#577d36] text-white p-3 rounded font-bold hover:bg-[#689243] mt-2">
                    {authTab === 'login' ? 'Войти' : 'Зарегистрироваться'}
                </button>
            </div>
        </div>
    );
}