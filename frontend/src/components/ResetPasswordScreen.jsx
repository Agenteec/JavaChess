import React, { useState } from 'react';
import { ShieldAlert, CheckCircle2, KeyRound } from 'lucide-react';

export default function ResetPasswordScreen({ userServiceUrl, token, onDone }) {
    const [password, setPassword] = useState('');
    const [confirm, setConfirm] = useState('');
    const [dangerAlert, setDangerAlert] = useState('');
    const [successAlert, setSuccessAlert] = useState('');
    const [busy, setBusy] = useState(false);

    const submit = async () => {
        setDangerAlert(''); setSuccessAlert('');
        if (password.length < 6) { setDangerAlert("Пароль должен быть не короче 6 символов."); return; }
        if (password !== confirm) { setDangerAlert("Пароли не совпадают."); return; }

        setBusy(true);
        try {
            const res = await fetch(userServiceUrl + '/reset-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token, password })
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) throw new Error(data.message || 'Не удалось сбросить пароль.');
            setSuccessAlert(data.message || "Пароль изменён. Перенаправляем ко входу…");
            setTimeout(onDone, 2500);
        } catch (err) {
            setDangerAlert(err.message);
        } finally {
            setBusy(false);
        }
    };

    const inputCls = "bg-[#15130F] border border-[#322C24] text-white p-3 rounded focus:outline-none focus:border-[#6E9F4A]";

    return (
        <div className="flex flex-col gap-4 w-full max-w-[380px] mt-10">
            <div className="bg-[#201D17] border border-[#322C24] rounded-lg p-7 shadow-xl flex flex-col gap-4">
                <h2 className="text-xl font-bold text-white text-center inline-flex items-center justify-center gap-2">
                    <KeyRound className="w-5 h-5 text-[#E8C56A]" /> Новый пароль
                </h2>

                {dangerAlert && <div className="bg-[#4a1c1c] border border-red-500 text-red-200 p-3 rounded text-sm text-center"><ShieldAlert className="inline w-4 h-4 mr-1"/> {dangerAlert}</div>}
                {successAlert && <div className="bg-[#1c3d24] border border-green-500 text-green-200 p-3 rounded text-sm text-center"><CheckCircle2 className="inline w-4 h-4 mr-1"/> {successAlert}</div>}

                {!successAlert && (
                    <>
                        <div className="flex flex-col gap-1">
                            <label className="text-xs font-bold text-[#9A958C]">Новый пароль</label>
                            <input type="password" value={password} onChange={e => setPassword(e.target.value)} className={inputCls} placeholder="Минимум 6 символов" />
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-xs font-bold text-[#9A958C]">Повторите пароль</label>
                            <input type="password" value={confirm} onChange={e => setConfirm(e.target.value)} onKeyDown={e => e.key === 'Enter' && submit()} className={inputCls} placeholder="Ещё раз" />
                        </div>
                        <button onClick={submit} disabled={busy} className="bg-[#6E9F4A] text-white p-3 rounded font-bold hover:bg-[#7DB356] mt-1 disabled:opacity-50">
                            {busy ? 'Подождите…' : 'Сменить пароль'}
                        </button>
                    </>
                )}
            </div>
        </div>
    );
}
