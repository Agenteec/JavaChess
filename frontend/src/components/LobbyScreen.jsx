import React, { useState, useEffect } from 'react';
import { Play, Trophy, Clock, Settings } from 'lucide-react';

export default function LobbyScreen({ userBaseUrl, onStartMatch, onOpenProfile, onOpenCustomModal, isGuest }) {
    const [leaderCategory, setLeaderCategory] = useState('RAPID');
    const [leaders, setLeaders] = useState([]);
    const [isLeadersLoading, setIsLeadersLoading] = useState(false);

    const [challenges, setChallenges] = useState([]);

    const [showChallengeModal, setShowChallengeModal] = useState(false);
    const [selectedChallenge, setSelectedChallenge] = useState(null);

    useEffect(() => {
        loadLeaderboard('RAPID');
        loadLobbyChallenges();

        const interval = setInterval(() => {
            loadLobbyChallenges();
        }, 5000);

        return () => clearInterval(interval);
    }, []);

    const loadLeaderboard = (category) => {
        setLeaderCategory(category);
        setIsLeadersLoading(true);
        fetch(`${userBaseUrl}/leaderboard?category=${category}`)
            .then(res => res.json())
            .then(data => {
                setLeaders(data);
                setIsLeadersLoading(false);
            })
            .catch(() => setIsLeadersLoading(false));
    };

    const loadLobbyChallenges = () => {
        const isLocal = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
        const lobbyUrl = isLocal ? "http://localhost:8082/api/v1/lobby/challenges" : window.location.origin + "/api/v1/lobby/challenges";

        fetch(lobbyUrl)
            .then(res => res.json())
            .then(data => {
                setChallenges(data);
            })
            .catch(err => console.error("Ошибка обновления лобби вызовов: ", err));
    };

    const handleRowClick = (roomId, player, time, type) => {
        setSelectedChallenge({ roomId, player, time, type });
        setShowChallengeModal(true);
    };

    return (
        <div className="w-full grid grid-cols-1 md:grid-cols-3 gap-6">

            <section className="bg-[#201D17] border border-[#322C24] rounded-lg p-5 flex flex-col shadow-lg">
                <div className="text-lg font-bold text-white border-b border-[#322C24] pb-2 mb-4 flex items-center gap-2">
                    <Play className="w-5 h-5 text-[#6E9F4A]" /> Быстрый старт
                </div>
                <div className="grid grid-cols-2 gap-2.5">
                    {[
                        { cat: 'BULLET', m: 1, i: 0, label: 'Пуля', sub: '1+0' },
                        { cat: 'BULLET', m: 2, i: 1, label: 'Пуля', sub: '2+1' },
                        { cat: 'BLITZ', m: 3, i: 0, label: 'Блиц', sub: '3+0' },
                        { cat: 'BLITZ', m: 3, i: 2, label: 'Блиц', sub: '3+2' },
                        { cat: 'BLITZ', m: 5, i: 0, label: 'Блиц', sub: '5+0' },
                        { cat: 'BLITZ', m: 5, i: 3, label: 'Блиц', sub: '5+3' },
                        { cat: 'RAPID', m: 10, i: 0, label: 'Рапид', sub: '10+0' },
                        { cat: 'RAPID', m: 10, i: 5, label: 'Рапид', sub: '10+5' },
                        { cat: 'RAPID', m: 15, i: 10, label: 'Рапид', sub: '15+10' },
                        { cat: 'CLASSICAL', m: 30, i: 0, label: 'Классика', sub: '30+0' },
                        { cat: 'CLASSICAL', m: 30, i: 20, label: 'Классика', sub: '30+20' }
                    ].map((item, idx) => (
                        <button key={idx} onClick={() => onStartMatch(item.cat, item.m, item.i)} className="bg-[#15130F] border border-[#322C24] rounded-lg p-3.5 text-center cursor-pointer hover:bg-[#1A1711] hover:scale-[1.02] transition-all">
                            <div className="font-bold text-white text-[15px]">{item.label}</div>
                            <div className="text-[11px] text-[#9A958C] mt-1 tabular-nums">{item.sub}</div>
                        </button>
                    ))}
                    <button onClick={onOpenCustomModal} className="bg-[#1A1711] border border-[#322C24] rounded-lg p-3.5 text-center cursor-pointer hover:bg-[#2A251E] hover:scale-[1.02] transition-all">
                        <div className="font-bold text-[#E8C56A] text-[15px]">Своя игра</div>
                        <div className="text-[10px] text-[#9A958C] mt-1 inline-flex items-center gap-1"><Settings className="w-3 h-3" /> Настроить</div>
                    </button>
                </div>
            </section>

            <section className="bg-[#201D17] border border-[#322C24] rounded-lg p-5 flex flex-col shadow-lg">
                <div className="text-lg font-bold text-white border-b border-[#322C24] pb-2 mb-4 flex items-center gap-2">
                    <Clock className="w-5 h-5 text-[#E8C56A]" /> Зал ожидания
                </div>
                <div className="overflow-x-auto flex-1">
                    <table className="w-full border-collapse text-sm text-left">
                        <thead>
                        <tr className="text-white font-semibold border-b border-[#322C24]">
                            <th className="py-2.5">Игрок</th>
                            <th>Время</th>
                            <th>Тип вызова</th>
                            <th className="text-right">Рейтинг</th>
                        </tr>
                        </thead>
                        <tbody>
                        {challenges.length === 0 ? (
                            <tr><td colSpan="4" className="text-center py-5 text-[#9A958C] italic">Нет активных вызовов</td></tr>
                        ) : (
                            challenges.map((c, i) => (
                                <tr key={i} onClick={() => handleRowClick(c.roomId, c.player, c.time, c.type)} className="cursor-pointer hover:bg-[#1A1711] transition-colors">
                                    <td className="py-3 font-bold text-[#E8C56A]">{c.player}</td>
                                    <td>{c.time}</td>
                                    <td className="text-[#E8C56A] font-bold">
                                        {c.type}
                                        <span className={`ml-1.5 text-[9px] font-bold px-1.5 py-0.5 rounded ${c.rated === false ? 'bg-[#322C24] text-[#9A958C]' : 'bg-[#6E9F4A]/30 text-[#8fbf6a]'}`}>
                                            {c.rated === false ? 'товар.' : 'рейт.'}
                                        </span>
                                    </td>
                                    <td className="text-right text-white font-bold">{c.rating}</td>
                                </tr>
                            ))
                        )}
                        </tbody>
                    </table>
                </div>
            </section>

            <section className="bg-[#201D17] border border-[#322C24] rounded-lg p-5 flex flex-col shadow-lg">
                <div className="text-lg font-bold text-white border-b border-[#322C24] pb-2 mb-4 flex items-center gap-2">
                    <Trophy className="w-5 h-5 text-[#E8C56A]" /> Топ-10 платформы
                </div>

                <div className="flex gap-1 mb-4">
                    {['BULLET', 'BLITZ', 'RAPID', 'CLASSICAL'].map(cat => (
                        <button key={cat} onClick={() => loadLeaderboard(cat)} className={`flex-1 py-1.5 px-0.5 text-[10px] font-bold rounded cursor-pointer transition-colors ${leaderCategory === cat ? 'bg-[#6E9F4A] text-white' : 'bg-[#1A1711] text-[#E9E5DD]'}`}>{cat === 'BULLET' ? 'Пуля' : cat === 'BLITZ' ? 'Блиц' : cat === 'RAPID' ? 'Рапид' : 'Класс.'}</button>
                    ))}
                </div>

                <div className="overflow-x-auto flex-1">
                    <table className="w-full border-collapse text-sm text-left">
                        <thead>
                        <tr className="text-white font-semibold border-b border-[#322C24]">
                            <th className="w-10 py-1.5">#</th>
                            <th>Игрок</th>
                            <th className="text-right">Elo</th>
                        </tr>
                        </thead>
                        <tbody>
                        {isLeadersLoading ? (
                            <tr><td colSpan="3" className="text-center py-5 text-[#9A958C]">Загрузка...</td></tr>
                        ) : leaders.length === 0 ? (
                            <tr><td colSpan="3" className="text-center py-5 text-[#9A958C]">Лидеров нет</td></tr>
                        ) : (
                            leaders.map((u, i) => (
                                <tr key={u.id} onClick={() => onOpenProfile(u.username)} className="cursor-pointer hover:bg-[#1A1711] transition-colors">
                                    <td className={`font-bold py-1.5 ${i === 0 ? 'text-[#E8C56A]' : 'text-[#9A958C]'}`}>{i + 1}</td>
                                    <td className="font-bold text-white underline hover:text-[#E8C56A]">{u.username}</td>
                                    <td className="text-right text-[#E8C56A] font-bold">{u["rating" + leaderCategory.charAt(0) + leaderCategory.slice(1).toLowerCase()] || 1500}</td>
                                </tr>
                            ))
                        )}
                        </tbody>
                    </table>
                </div>
            </section>

            {showChallengeModal && selectedChallenge && (
                <div className="fixed inset-0 bg-black/85 backdrop-blur-sm flex justify-center items-center z-[3000] p-4" onClick={() => setShowChallengeModal(false)}>
                    <div className="bg-[#201D17] border border-[#322C24] rounded-xl p-6 max-w-[340px] w-full text-center flex flex-col gap-4 shadow-2xl" onClick={e => e.stopPropagation()}>
                        <h3 className="text-xl font-bold text-[#E8C56A]">Вызов на партию</h3>
                        <div className="bg-[#15130F] border border-[#322C24] rounded-lg p-4 flex flex-col gap-2">
                            <div className="text-lg font-bold text-[#E8C56A]">{selectedChallenge.player}</div>
                            <div className="flex justify-center gap-4 text-sm">
                                <span className="text-[#E8C56A] font-bold">{selectedChallenge.type}</span>
                                <span className="text-white font-bold">Рейтинг: {selectedChallenge.rating}</span>
                            </div>
                        </div>
                        <div className="flex gap-3">
                            <button
                                onClick={() => { window.location.href = `/?room=${selectedChallenge.roomId}`; }}
                                className="flex-1 bg-[#6E9F4A] text-white p-3 rounded-lg font-bold hover:bg-[#7DB356] transition-colors"
                            >
                                Принять вызов
                            </button>
                            <button
                                onClick={() => setShowChallengeModal(false)}
                                className="flex-1 bg-[#2A251E] text-white p-3 rounded-lg font-bold hover:bg-[#37322A] transition-colors"
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