import React, { useState, useEffect, useRef, lazy, Suspense } from 'react';
import { Dices, LogIn, LogOut } from 'lucide-react';
import AuthScreen from './components/AuthScreen.jsx';
import LobbyScreen from './components/LobbyScreen.jsx';
import ChessGame from './components/ChessGame.jsx';
import ResetPasswordScreen from './components/ResetPasswordScreen.jsx';

const ProfileScreen = lazy(() => import('./components/ProfileScreen.jsx'));

export default function App() {
    const [screen, setScreen] = useState('LOBBY');
    const [activeUser, setActiveUser] = useState(null);

    const [isGuest, setIsGuest] = useState(localStorage.getItem("is_guest") === "true");

    const savedRating = localStorage.getItem("rating");
    const [activeUserRating, setActiveUserRating] = useState(savedRating ? parseInt(savedRating, 10) : 1500);

    const [activeRoomId, setActiveRoomId] = useState(null);
    const [targetProfileUser, setTargetProfileUser] = useState(null);
    const [resetToken, setResetToken] = useState(null);

    const [showMatchmakingModal, setShowMatchmakingModal] = useState(false);
    const [matchmakingCategory, setMatchmakingCategory] = useState('');
    const [matchmakingTimer, setMatchmakingTimer] = useState('00:00');

    const [showCustomModal, setShowCustomModal] = useState(false);
    const [customMins, setCustomMins] = useState(10);
    const [customInc, setCustomInc] = useState(0);
    const [customRated, setCustomRated] = useState(false);
    const [customColor, setCustomColor] = useState('RANDOM');
    const [customVariant, setCustomVariant] = useState('STANDARD');
    const [customLobby, setCustomLobby] = useState(true);

    const isLocal = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
    const USER_BASE_URL = isLocal ? "http://localhost:8081/api/v1/users" : window.location.origin + "/api/v1/users";
    const USER_SERVICE_URL = isLocal ? "http://localhost:8081/api/v1/auth" : window.location.origin + "/api/v1/auth";

    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search);
        const room = urlParams.get('room');
        const user = urlParams.get('user');

        const resetTok = urlParams.get('reset');
        if (resetTok) {
            setResetToken(resetTok);
            setScreen('RESET');
            return;
        }

        const token = localStorage.getItem("jwt_token");
        const savedUser = localStorage.getItem("username");
        const savedIsGuest = localStorage.getItem("is_guest");

        if (token && savedUser) {
            setActiveUser(savedUser);
            const isUserGuest = savedIsGuest === "true";
            setIsGuest(isUserGuest);

            if (savedRating) {
                setActiveUserRating(parseInt(savedRating, 10));
            }

            if (!isUserGuest) {
                fetchRating(savedUser);
            }

            if (room) {
                setActiveRoomId(room);
                setScreen('PLAY');
            } else if (user) {
                setTargetProfileUser(user);
                setScreen('PROFILE');
            } else {
                setScreen('LOBBY');
            }
        } else {
            fetch(USER_SERVICE_URL + "/guest", { method: 'POST' })
                .then(res => {
                    if (!res.ok) throw new Error("Ошибка сервера при создании гостя");
                    return res.json();
                })
                .then(data => {
                    localStorage.setItem("jwt_token", data.token);
                    localStorage.setItem("username", data.username);
                    localStorage.setItem("rating", data.rating);
                    localStorage.setItem("is_guest", "true");

                    setActiveUser(data.username);
                    setIsGuest(true);

                    if (room) {
                        setActiveRoomId(room);
                        setScreen('PLAY');
                    } else if (user) {
                        setTargetProfileUser(user);
                        setScreen('PROFILE');
                    } else {
                        setScreen('LOBBY');
                    }
                })
                .catch(err => console.error("Ошибка получения гостевого токена: " + err.message));
        }
    }, []);

    const fetchRating = (username) => {
        fetch(`${USER_BASE_URL}/${username}`)
            .then(res => res.json())
            .then(user => {
                setActiveUserRating(user.ratingRapid);
                localStorage.setItem("rating", user.ratingRapid);
            })
            .catch(err => console.error("Error updating ELO: " + err.message));
    };

    const navigateTo = (newScreen, params = {}) => {
        window.history.pushState({}, '', window.location.pathname);
        if (params.room) setActiveRoomId(params.room);
        if (params.user) setTargetProfileUser(params.user);
        setScreen(newScreen);
    };

    const handleAuthSuccess = (username, rating) => {
        setActiveUser(username);
        setActiveUserRating(rating);
        setIsGuest(false);
        localStorage.setItem("is_guest", "false");
        navigateTo('LOBBY');
    };

    const logout = () => {
        localStorage.clear();
        setActiveUser(null);
        setIsGuest(false);
        navigateTo('AUTH');
    };

    const matchmakingIntervalRef = useRef(null);
    const matchmakingSocketRef = useRef(null);
    const matchmakingPingRef = useRef(null);

    const teardownMatchmaking = () => {
        if (matchmakingIntervalRef.current) {
            clearInterval(matchmakingIntervalRef.current);
            matchmakingIntervalRef.current = null;
        }
        if (matchmakingPingRef.current) {
            clearInterval(matchmakingPingRef.current);
            matchmakingPingRef.current = null;
        }
        const sock = matchmakingSocketRef.current;
        if (sock) {
            sock.onopen = sock.onmessage = sock.onclose = sock.onerror = null;
            try { sock.close(); } catch (e) { /* noop */ }
            matchmakingSocketRef.current = null;
        }
    };

    useEffect(() => teardownMatchmaking, []);

    const startQuickMatch = (category, minutes, increment) => {
        teardownMatchmaking();

        const user = activeUser || localStorage.getItem("guest_username") || "Guest_" + Math.floor(1000 + Math.random() * 9000);
        setMatchmakingCategory(`${category} (${minutes}+${increment})`);
        setShowMatchmakingModal(true);
        setMatchmakingTimer("00:00");

        let seconds = 0;
        matchmakingIntervalRef.current = setInterval(() => {
            seconds++;
            const mins = Math.floor(seconds / 60).toString().padStart(2, '0');
            const secs = (seconds % 60).toString().padStart(2, '0');
            setMatchmakingTimer(`${mins}:${secs}`);
        }, 1000);

        const wsProtocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
        const wsUrl = wsProtocol + window.location.host + "/game";

        const socket = new WebSocket(wsUrl);
        matchmakingSocketRef.current = socket;
        socket.onopen = () => {
            socket.send(JSON.stringify({
                action: "QUEUE",
                category,
                username: user,
                minutes,
                increment,
                token: localStorage.getItem("jwt_token") || undefined
            }));
            matchmakingPingRef.current = setInterval(() => {
                if (socket.readyState === WebSocket.OPEN) {
                    socket.send(JSON.stringify({ action: "PING" }));
                }
            }, 25000);
        };
        socket.onmessage = (event) => {
            const data = JSON.parse(event.data);
            if (data.type === "REDIRECT") {
                teardownMatchmaking();
                setShowMatchmakingModal(false);
                window.location.href = data.url;
            }
        };
        socket.onclose = () => {
            if (matchmakingSocketRef.current === socket) {
                matchmakingSocketRef.current = null;
                cleanupMatchmaking();
            }
        };
    };

    const cancelMatchmaking = () => {
        teardownMatchmaking();
        setShowMatchmakingModal(false);
    };

    const cleanupMatchmaking = () => {
        if (matchmakingIntervalRef.current) {
            clearInterval(matchmakingIntervalRef.current);
            matchmakingIntervalRef.current = null;
        }
        setShowMatchmakingModal(false);
    };

    const createCustomGame = () => {
        const gameUuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
        const rated = customRated && !isGuest;
        const color = rated ? 'RANDOM' : customColor;
        const lobby = rated ? true : customLobby;
        setShowCustomModal(false);
        const params = new URLSearchParams({
            room: gameUuid,
            mins: String(customMins),
            inc: String(customInc),
            rated: String(rated),
            color,
            variant: customVariant,
            lobby: String(lobby)
        });
        window.location.href = `/?${params.toString()}`;
    };

    const isRegistered = activeUser && !isGuest;

    return (
        <div className="min-h-screen w-full flex flex-col items-center bg-[#15130F] select-none text-[#E9E5DD]">

            <header className="w-full bg-[#201D17] border-b border-[#322C24] flex justify-center shadow-md z-50">
                <div className="w-full max-w-[1200px] flex justify-between items-center px-5 py-3">
                    <button onClick={() => window.location.href = '/'} className="text-xl font-bold text-white hover:text-[#E8C56A] bg-transparent border-none cursor-pointer inline-flex items-center gap-2"><span className="text-2xl leading-none text-[#E8C56A]">♞</span> Agenteec Chess</button>
                    <div className="flex items-center gap-4">
                        {isRegistered ? (
                            <>
                                <span className="font-bold text-[#E8C56A] cursor-pointer hover:text-white hover:underline" onClick={() => navigateTo('PROFILE', { user: activeUser })}>{activeUser}</span>
                                <button onClick={logout} className="bg-[#2A251E] text-white px-3 py-1.5 rounded text-sm font-bold cursor-pointer hover:bg-[#37322A] inline-flex items-center gap-1.5"><LogOut className="w-4 h-4" /> Выйти</button>
                            </>
                        ) : (
                            <>
                                <span className="text-sm italic text-[#9A958C]">{activeUser || "Аноним"}</span>
                                <button onClick={() => navigateTo('AUTH')} className="bg-[#6E9F4A] text-white px-3 py-1.5 rounded text-sm font-bold cursor-pointer hover:bg-[#7DB356] inline-flex items-center gap-1.5"><LogIn className="w-4 h-4" /> Войти</button>
                            </>
                        )}
                    </div>
                </div>
            </header>

            <div className={`flex-1 w-full max-w-[1200px] flex flex-col items-center ${screen === 'PLAY' ? 'p-2 md:p-3 justify-start' : 'p-5 justify-center'}`}>
                {screen === 'AUTH' && (
                    <AuthScreen
                        userServiceUrl={USER_SERVICE_URL}
                        onAuthSuccess={handleAuthSuccess}
                    />
                )}
                {screen === 'RESET' && (
                    <ResetPasswordScreen
                        userServiceUrl={USER_SERVICE_URL}
                        token={resetToken}
                        onDone={() => { window.history.pushState({}, '', window.location.pathname); setScreen('AUTH'); }}
                    />
                )}
                {screen === 'LOBBY' && (
                    <LobbyScreen
                        userBaseUrl={USER_BASE_URL}
                        onStartMatch={startQuickMatch}
                        onOpenProfile={(user) => navigateTo('PROFILE', { user })}
                        onOpenCustomModal={() => setShowCustomModal(true)}
                        isGuest={isGuest}
                    />
                )}
                {screen === 'PLAY' && (
                    <ChessGame
                        roomId={activeRoomId}
                        username={activeUser || localStorage.getItem("guest_username")}
                        onExit={() => window.location.href = '/'}
                        initialMins={new URLSearchParams(window.location.search).get('mins')}
                        initialInc={new URLSearchParams(window.location.search).get('inc')}
                        initialRated={new URLSearchParams(window.location.search).get('rated')}
                        initialColor={new URLSearchParams(window.location.search).get('color')}
                        initialVariant={new URLSearchParams(window.location.search).get('variant')}
                        initialLobby={new URLSearchParams(window.location.search).get('lobby')}
                        isGuest={isGuest}
                    />
                )}
                {screen === 'PROFILE' && (
                    <Suspense fallback={<div className="text-white text-center py-10">Загрузка профиля...</div>}>
                        <ProfileScreen
                            targetUser={targetProfileUser}
                            userBaseUrl={USER_BASE_URL}
                            onBack={() => window.location.href = '/'}
                            onViewGame={(roomId) => navigateTo('PLAY', { room: roomId })}
                        />
                    </Suspense>
                )}
            </div>

            {showMatchmakingModal && (
                <div className="fixed inset-0 bg-black/85 backdrop-blur-sm flex justify-center items-center z-[3000] p-4">
                    <div className="bg-[#201D17] border border-[#322C24] rounded-lg p-6 max-w-[320px] w-full text-center flex flex-col gap-4 shadow-2xl">
                        <h3 className="text-xl font-bold text-[#E8C56A]">Поиск противника</h3>
                        <div className="text-xs text-[#9A958C]">Режим: {matchmakingCategory}</div>
                        <div className="flex justify-center items-center my-4">
                            <div className="w-14 h-14 border-4 border-[#322C24] border-t-[#6E9F4A] rounded-full animate-spin"></div>
                        </div>
                        <div className="text-2xl font-bold text-white">{matchmakingTimer}</div>
                        <button onClick={cancelMatchmaking} className="bg-red-500 text-white p-3 rounded font-bold hover:bg-red-600">Отменить поиск</button>
                    </div>
                </div>
            )}

            {showCustomModal && (
                <div className="fixed inset-0 bg-black/85 backdrop-blur-sm flex justify-center items-center z-[3000] p-4">
                    <div className="bg-[#201D17] border border-[#322C24] rounded-lg p-6 max-w-[380px] w-full max-h-[90vh] overflow-y-auto text-center flex flex-col gap-4 shadow-2xl">
                        <h3 className="text-xl font-bold text-[#E8C56A]">Настройка своей игры</h3>

                        <div className="flex flex-col gap-4 text-left">
                            <div className="flex flex-col gap-1.5">
                                <label className="text-xs font-bold text-[#9A958C]">Тип партии</label>
                                <div className="flex gap-2">
                                    <button onClick={() => setCustomRated(false)} className={`flex-1 p-2 rounded text-sm font-bold transition-colors ${!customRated ? 'bg-[#6E9F4A] text-white' : 'bg-[#15130F] text-[#E9E5DD] border border-[#322C24]'}`}>Товарищеская</button>
                                    <button
                                        onClick={() => !isGuest && setCustomRated(true)}
                                        disabled={isGuest}
                                        title={isGuest ? 'Доступно только зарегистрированным' : undefined}
                                        className={`flex-1 p-2 rounded text-sm font-bold transition-colors ${customRated ? 'bg-[#6E9F4A] text-white' : 'bg-[#15130F] text-[#E9E5DD] border border-[#322C24]'} ${isGuest ? 'opacity-40 cursor-not-allowed' : ''}`}
                                    >Рейтинговая</button>
                                </div>
                                {isGuest && <span className="text-[10px] text-[#9A958C] italic">Рейтинговые партии — только для зарегистрированных игроков.</span>}
                            </div>

                            <div className="flex flex-col gap-1.5">
                                <label className="text-xs font-bold text-[#9A958C]">Вариант</label>
                                <select
                                    value={customVariant}
                                    onChange={e => setCustomVariant(e.target.value)}
                                    className="bg-[#15130F] border border-[#322C24] text-white p-2 rounded text-sm outline-none focus:border-[#6E9F4A]"
                                >
                                    <option value="STANDARD">Стандартные шахматы</option>
                                    <option value="CHESS960" disabled>Шахматы Фишера (скоро)</option>
                                    <option value="KINGOFTHEHILL" disabled>Гонка королей (скоро)</option>
                                </select>
                            </div>

                            {!customRated && (
                                <div className="flex flex-col gap-1.5">
                                    <label className="text-xs font-bold text-[#9A958C]">Ваш цвет</label>
                                    <div className="flex gap-2">
                                        {[
                                            { v: 'WHITE', l: 'Белые', icon: <span className="text-base leading-none">♔</span> },
                                            { v: 'RANDOM', l: 'Случайно', icon: <Dices className="w-4 h-4" /> },
                                            { v: 'BLACK', l: 'Чёрные', icon: <span className="text-base leading-none">♚</span> }
                                        ].map(o => (
                                            <button key={o.v} onClick={() => setCustomColor(o.v)} className={`flex-1 p-2 rounded text-xs font-bold transition-colors inline-flex flex-col items-center justify-center gap-1 ${customColor === o.v ? 'bg-[#6E9F4A] text-white' : 'bg-[#15130F] text-[#E9E5DD] border border-[#322C24]'}`}>{o.icon}{o.l}</button>
                                        ))}
                                    </div>
                                </div>
                            )}

                            <div className="flex flex-col gap-1">
                                <label className="text-xs font-bold text-[#9A958C]">Минут на партию: <span className="text-white text-sm font-semibold">{customMins}</span></label>
                                <input type="range" min="1" max="60" value={customMins} onChange={e => setCustomMins(parseInt(e.target.value))} className="accent-[#6E9F4A] cursor-pointer h-1.5 w-full bg-[#15130F] rounded-lg appearance-none" />
                            </div>
                            <div className="flex flex-col gap-1">
                                <label className="text-xs font-bold text-[#9A958C]">Добавление на ход (сек): <span className="text-white text-sm font-semibold">{customInc}</span></label>
                                <input type="range" min="0" max="60" value={customInc} onChange={e => setCustomInc(parseInt(e.target.value))} className="accent-[#6E9F4A] cursor-pointer h-1.5 w-full bg-[#15130F] rounded-lg appearance-none" />
                            </div>


                            {!customRated && (
                                <label className="flex items-center justify-between gap-2 cursor-pointer">
                                    <span className="text-xs font-bold text-[#9A958C]">Показывать в лобби</span>
                                    <input type="checkbox" checked={customLobby} onChange={e => setCustomLobby(e.target.checked)} className="accent-[#6E9F4A] w-4 h-4 cursor-pointer" />
                                </label>
                            )}
                            {customRated && <span className="text-[10px] text-[#9A958C] italic">Рейтинговая: цвет случайный, всегда видна в лобби.</span>}
                        </div>

                        <div className="flex gap-3 mt-2">
                            <button onClick={createCustomGame} className="flex-1 bg-[#6E9F4A] text-white p-3 rounded font-bold hover:bg-[#7DB356]">Создать игру</button>
                            <button onClick={() => setShowCustomModal(false)} className="flex-1 bg-[#2A251E] text-white p-3 rounded font-bold hover:bg-[#37322A]">Отмена</button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}