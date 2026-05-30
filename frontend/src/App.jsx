import React, { useState, useEffect } from 'react';
import AuthScreen from './components/AuthScreen.jsx';
import LobbyScreen from './components/LobbyScreen.jsx';
import ProfileScreen from './components/ProfileScreen.jsx';
import ChessGame from './components/ChessGame.jsx';

export default function App() {
    const [screen, setScreen] = useState('LOBBY');
    const [activeUser, setActiveUser] = useState(null);

    const savedRating = localStorage.getItem("rating");
    const [activeUserRating, setActiveUserRating] = useState(savedRating ? parseInt(savedRating, 10) : 1500);

    const [activeRoomId, setActiveRoomId] = useState(null);
    const [targetProfileUser, setTargetProfileUser] = useState(null);

    const [showMatchmakingModal, setShowMatchmakingModal] = useState(false);
    const [matchmakingCategory, setMatchmakingCategory] = useState('');
    const [matchmakingTimer, setMatchmakingTimer] = useState('00:00');

    const [showCustomModal, setShowCustomModal] = useState(false);
    const [customMins, setCustomMins] = useState(10);
    const [customInc, setCustomInc] = useState(0);

    const isLocal = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
    const USER_BASE_URL = isLocal ? "http://localhost:8081/api/v1/users" : window.location.origin + "/api/v1/users";
    const USER_SERVICE_URL = isLocal ? "http://localhost:8081/api/v1/auth" : window.location.origin + "/api/v1/auth";

    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search);
        const room = urlParams.get('room');
        const user = urlParams.get('user');

        let guestUser = localStorage.getItem("guest_username");
        if (!guestUser) {
            guestUser = "Guest_" + Math.floor(1000 + Math.random() * 9000);
            localStorage.setItem("guest_username", guestUser);
        }

        const token = localStorage.getItem("jwt_token");
        const savedUser = localStorage.getItem("username");

        if (token && savedUser) {
            setActiveUser(savedUser);
            if (savedRating) {
                setActiveUserRating(parseInt(savedRating, 10));
            }
            fetchRating(savedUser);
        }

        if (room) {
            setActiveRoomId(room);
            setScreen('PLAY');
        } else if (user) {
            setTargetProfileUser(user);
            setScreen('PROFILE');
        } else {
            if (token && savedUser) {
                setScreen('LOBBY');
            } else {
                setScreen('AUTH');
            }
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
        navigateTo('LOBBY');
    };

    const logout = () => {
        localStorage.clear();
        setActiveUser(null);
        navigateTo('AUTH');
    };

    let matchmakingInterval = null;
    let matchmakingSeconds = 0;
    let matchmakingSocket = null;

    const startQuickMatch = (category, minutes, increment) => {
        const user = activeUser || localStorage.getItem("guest_username") || "Guest_" + Math.floor(1000 + Math.random() * 9000);
        setMatchmakingCategory(`${category} (${minutes}+${increment})`);
        setShowMatchmakingModal(true);
        setMatchmakingTimer("00:00");

        matchmakingSeconds = 0;
        matchmakingInterval = setInterval(() => {
            matchmakingSeconds++;
            const mins = Math.floor(matchmakingSeconds / 60).toString().padStart(2, '0');
            const secs = (matchmakingSeconds % 60).toString().padStart(2, '0');
            setMatchmakingTimer(`${mins}:${secs}`);
        }, 1000);

        const wsProtocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
        const wsUrl = wsProtocol + window.location.host + "/game";

        matchmakingSocket = new WebSocket(wsUrl);
        matchmakingSocket.onopen = () => {
            matchmakingSocket.send(JSON.stringify({
                action: "QUEUE", category, username: user, minutes, increment
            }));
        };
        matchmakingSocket.onmessage = (event) => {
            const data = JSON.parse(event.data);
            if (data.type === "REDIRECT") {
                cleanupMatchmaking();
                window.location.href = data.url;
            }
        };
        matchmakingSocket.onclose = () => cleanupMatchmaking();
    };

    const cancelMatchmaking = () => {
        if (matchmakingSocket) matchmakingSocket.close();
        cleanupMatchmaking();
    };

    const cleanupMatchmaking = () => {
        if (matchmakingInterval) clearInterval(matchmakingInterval);
        setShowMatchmakingModal(false);
    };

    const createCustomGame = (mins, inc) => {
        const gameUuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
        setShowCustomModal(false);
        window.location.href = `/?room=${gameUuid}&mins=${mins}&inc=${inc}`;
    };

    return (
        <div className="min-h-screen w-full flex flex-col items-center bg-[#161512] select-none text-[#bababa]">

            <header className="w-full bg-[#1e1c18] border-b border-[#2a2824] flex justify-center shadow-md z-50">
                <div className="w-full max-w-[1200px] flex justify-between items-center px-5 py-3">
                    <button onClick={() => window.location.href = '/'} className="text-xl font-bold text-white hover:text-[#f0d9b5] bg-transparent border-none cursor-pointer">♞ Agenteec Chess</button>
                    <div className="flex items-center gap-4">
                        {activeUser ? (
                            <>
                                <span className="font-bold text-[#f0d9b5] cursor-pointer underline hover:text-white" onClick={() => navigateTo('PROFILE', { user: activeUser })}>{activeUser}</span>
                                <button onClick={logout} className="bg-[#363431] text-white px-3 py-1.5 rounded text-sm font-bold cursor-pointer hover:bg-[#45433f]">Выйти</button>
                            </>
                        ) : (
                            <>
                                <span className="text-sm italic text-[#8b8985]">{localStorage.getItem("guest_username") || "Аноним"}</span>
                                <button onClick={() => navigateTo('AUTH')} className="bg-[#577d36] text-white px-3 py-1.5 rounded text-sm font-bold cursor-pointer hover:bg-[#689243]">Войти</button>
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
                {screen === 'LOBBY' && (
                    <LobbyScreen
                        userBaseUrl={USER_BASE_URL}
                        onStartMatch={startQuickMatch}
                        onOpenProfile={(user) => navigateTo('PROFILE', { user })}
                        onOpenCustomModal={() => setShowCustomModal(true)}
                    />
                )}
                {screen === 'PLAY' && (
                    <ChessGame
                        roomId={activeRoomId}
                        username={activeUser || localStorage.getItem("guest_username")}
                        onExit={() => window.location.href = '/'}
                        initialMins={new URLSearchParams(window.location.search).get('mins')}
                        initialInc={new URLSearchParams(window.location.search).get('inc')}
                    />
                )}
                {screen === 'PROFILE' && (
                    <ProfileScreen
                        targetUser={targetProfileUser}
                        userBaseUrl={USER_BASE_URL}
                        onBack={() => window.location.href = '/'}
                    />
                )}
            </div>

            {showMatchmakingModal && (
                <div className="fixed inset-0 bg-black/85 backdrop-blur-sm flex justify-center items-center z-[3000] p-4">
                    <div className="bg-[#1e1c18] border border-[#2a2824] rounded-lg p-6 max-w-[320px] w-full text-center flex flex-col gap-4 shadow-2xl">
                        <h3 className="text-xl font-bold text-yellow-500">Поиск противника</h3>
                        <div className="text-xs text-[#8b8985]">Режим: {matchmakingCategory}</div>
                        <div className="flex justify-center items-center my-4">
                            <div className="w-14 h-14 border-4 border-[#2a2824] border-t-[#577d36] rounded-full animate-spin"></div>
                        </div>
                        <div className="text-2xl font-bold text-white">{matchmakingTimer}</div>
                        <button onClick={cancelMatchmaking} className="bg-red-500 text-white p-3 rounded font-bold hover:bg-red-600">Отменить поиск</button>
                    </div>
                </div>
            )}

            {showCustomModal && (
                <div className="fixed inset-0 bg-black/85 backdrop-blur-sm flex justify-center items-center z-[3000] p-4">
                    <div className="bg-[#1e1c18] border border-[#2a2824] rounded-lg p-6 max-w-[360px] w-full text-center flex flex-col gap-4 shadow-2xl">
                        <h3 className="text-xl font-bold text-yellow-500">Настройка своей игры</h3>

                        <div className="flex flex-col gap-4 text-left">
                            <div className="flex flex-col gap-1">
                                <label className="text-xs font-bold text-[#8b8985]">Минут на партию: <span className="text-white text-sm font-semibold">{customMins}</span></label>
                                <input
                                    type="range"
                                    min="1"
                                    max="60"
                                    value={customMins}
                                    onChange={e => setCustomMins(parseInt(e.target.value))}
                                    className="accent-[#577d36] cursor-pointer h-1.5 w-full bg-[#161512] rounded-lg appearance-none"
                                />
                            </div>

                            <div className="flex flex-col gap-1">
                                <label className="text-xs font-bold text-[#8b8985]">Добавление на ход (сек): <span className="text-white text-sm font-semibold">{customInc}</span></label>
                                <input
                                    type="range"
                                    min="0"
                                    max="60"
                                    value={customInc}
                                    onChange={e => setCustomInc(parseInt(e.target.value))}
                                    className="accent-[#577d36] cursor-pointer h-1.5 w-full bg-[#161512] rounded-lg appearance-none"
                                />
                            </div>
                        </div>

                        <div className="flex gap-3 mt-4">
                            <button
                                onClick={() => createCustomGame(customMins, customInc)}
                                className="flex-1 bg-[#577d36] text-white p-3 rounded font-bold hover:bg-[#689243]"
                            >
                                Создать игру
                            </button>
                            <button
                                onClick={() => setShowCustomModal(false)}
                                className="flex-1 bg-[#363431] text-white p-3 rounded font-bold hover:bg-[#45433f]"
                            >
                                Отмена
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
