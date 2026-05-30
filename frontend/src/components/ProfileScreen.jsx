import React, { useState, useEffect } from 'react';
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js';
import { Doughnut } from 'react-chartjs-2';

ChartJS.register(ArcElement, Tooltip, Legend);

export default function ProfileScreen({ targetUser, userBaseUrl, onBack }) {
    const [user, setUser] = useState(null);
    const [games, setGames] = useState([]);
    const [stats, setStats] = useState({ total: 0, wins: 0, draws: 0, losses: 0 });
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetch(`${userBaseUrl}/${targetUser}`)
            .then(res => res.json())
            .then(data => {
                setUser(data);
                return fetch(`${userBaseUrl}/${targetUser}/games`);
            })
            .then(res => res.json())
            .then(gamesList => {
                setGames(gamesList);
                calculateStats(gamesList);
                setLoading(false);
            })
            .catch(() => setLoading(false));
    }, [targetUser]);

    const calculateStats = (list) => {
        let total = list.length;
        let wins = 0;
        let draws = 0;
        let losses = 0;

        list.forEach(g => {
            if (g.result === "DRAW") draws++;
            else if ((g.result === "WHITE_WON" && g.whitePlayer === targetUser) ||
                (g.result === "BLACK_WON" && g.blackPlayer === targetUser)) {
                wins++;
            } else losses++;
        });
        setStats({ total, wins, draws, losses });
    };

    if (loading || !user) return <div className="text-white text-center py-10">Загрузка профиля...</div>;

    const chartData = {
        labels: ['Победы', 'Ничьи', 'Поражения'],
        datasets: [{
            data: [stats.wins, stats.draws, stats.losses],
            backgroundColor: ['#577d36', '#8b8985', '#dc3545'],
            borderColor: '#2a2824',
            borderWidth: 1
        }]
    };

    return (
        <div className="w-full flex flex-col gap-6 max-w-[1100px] mt-5">
            <div className="bg-[#1e1c18] border border-[#2a2824] rounded-lg p-6 flex justify-between items-center shadow-lg">
                <div>
                    <h1 className="text-2xl font-bold text-white">👤 {user.username}</h1>
                    <p className="text-xs text-[#8b8985] mt-1">Профиль шахматиста Agenteec Chess</p>
                </div>
                <button onClick={onBack} className="bg-[#363431] text-white px-4 py-2 rounded font-bold hover:bg-[#45433f]">Назад</button>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
                {[
                    { label: 'Bullet', rating: user.ratingBullet, games: user.gamesBullet },
                    { label: 'Blitz', rating: user.ratingBlitz, games: user.gamesBlitz },
                    { label: 'Rapid', rating: user.ratingRapid, games: user.gamesRapid },
                    { label: 'Classical', rating: user.ratingClassical, games: user.gamesClassical },
                    { label: 'Correspondence', rating: user.ratingCorrespondence, games: user.gamesCorrespondence }
                ].map((card, i) => (
                    <div key={i} className="bg-[#1e1c18] border border-[#2a2824] rounded-lg p-4 text-center shadow">
                        <div className="font-bold text-sm text-[#bababa]">{card.label}</div>
                        <div className="text-2xl font-bold text-[#ffcc00] my-1">{card.rating}{card.games < 10 ? '?' : ''}</div>
                        <div className="text-[11px] text-[#8b8985]">{card.games} партий</div>
                    </div>
                ))}
            </div>

            <div className="flex flex-col md:flex-row gap-5">
                <div className="bg-[#1e1c18] border border-[#2a2824] rounded-lg p-5 flex justify-center items-center flex-1 max-h-[300px]">
                    <div className="max-w-[200px] max-h-[200px]">
                        <Doughnut data={chartData} />
                    </div>
                </div>
                <div className="bg-[#1e1c18] border border-[#2a2824] rounded-lg p-5 flex-1.5 flex flex-col gap-3">
                    <h3 className="text-lg font-bold text-white border-b border-[#2a2824] pb-2">Статистика игр</h3>
                    <div className="flex justify-between border-b border-[#222] pb-2"><span>Всего сыграно</span> <strong>{stats.total}</strong></div>
                    <div className="flex justify-between border-b border-[#222] pb-2"><span>Победы</span> <strong className="text-green-400">{stats.wins}</strong></div>
                    <div className="flex justify-between border-b border-[#222] pb-2"><span>Ничьи</span> <strong>{stats.draws}</strong></div>
                    <div className="flex justify-between border-b border-[#222] pb-2"><span>Поражения</span> <strong className="text-red-400">{stats.losses}</strong></div>
                </div>
            </div>
        </div>
    );
}