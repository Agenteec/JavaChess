import React, { useState, useEffect, useMemo } from 'react';
import {
    Chart as ChartJS,
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    Title,
    Tooltip,
    Legend,
    Filler
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import { User, Share2, Check, ArrowLeft } from 'lucide-react';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler);

const parseDate = (raw) => {
    if (!raw) return null;
    if (Array.isArray(raw)) {
        const [y, mo, d, h = 0, mi = 0, s = 0] = raw;
        return new Date(y, (mo || 1) - 1, d, h, mi, s);
    }
    const dt = new Date(raw);
    return isNaN(dt.getTime()) ? null : dt;
};

const formatDate = (raw) => {
    const dt = parseDate(raw);
    if (!dt) return '—';
    return dt.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });
};

export default function ProfileScreen({ targetUser, userBaseUrl, onBack, onViewGame }) {
    const [user, setUser] = useState(null);
    const [games, setGames] = useState([]);
    const [loading, setLoading] = useState(true);
    const [shareCopied, setShareCopied] = useState(false);
    const [selectedCategory, setSelectedCategory] = useState('ALL');

    const shareProfile = async () => {
        const link = `${window.location.origin}/?user=${encodeURIComponent(targetUser)}`;
        try {
            await navigator.clipboard.writeText(link);
        } catch (e) {
            const ta = document.createElement('textarea');
            ta.value = link;
            document.body.appendChild(ta);
            ta.select();
            try { document.execCommand('copy'); } catch (_) {}
            document.body.removeChild(ta);
        }
        setShareCopied(true);
        setTimeout(() => setShareCopied(false), 2000);
    };

    useEffect(() => {
        setLoading(true);
        fetch(`${userBaseUrl}/${targetUser}`)
            .then(res => res.json())
            .then(data => {
                setUser(data);
                return fetch(`${userBaseUrl}/${targetUser}/games`);
            })
            .then(res => res.json())
            .then(gamesList => {
                setGames(Array.isArray(gamesList) ? gamesList : []);
                setLoading(false);
            })
            .catch(() => setLoading(false));
    }, [targetUser]);

    const sortedGames = useMemo(() => {
        return [...games].sort((a, b) => {
            const da = parseDate(a.endedAt);
            const db = parseDate(b.endedAt);
            return (da ? da.getTime() : 0) - (db ? db.getTime() : 0);
        });
    }, [games]);

    const categoryGames = useMemo(() => {
        if (selectedCategory === 'ALL') return sortedGames;
        return sortedGames.filter(g => (g.category || 'RAPID').toUpperCase() === selectedCategory);
    }, [sortedGames, selectedCategory]);

    const stats = useMemo(() => {
        const sortedGames = categoryGames;
        let wins = 0, draws = 0, losses = 0;
        let maxWinStreak = 0, maxLoseStreak = 0;
        let runWin = 0, runLose = 0;
        let curWinStreak = 0, curLoseStreak = 0;
        let bestRating = null, worstRating = null;

        const ratingPoints = [];

        sortedGames.forEach(g => {
            const isWhite = g.whitePlayer === targetUser;
            const ratingAfter = isWhite ? g.whiteRating : g.blackRating;
            if (ratingAfter != null) {
                ratingPoints.push(ratingAfter);
                bestRating = bestRating == null ? ratingAfter : Math.max(bestRating, ratingAfter);
                worstRating = worstRating == null ? ratingAfter : Math.min(worstRating, ratingAfter);
            }

            let outcome; // 'W' | 'D' | 'L'
            if (g.result === 'DRAW') outcome = 'D';
            else if ((g.result === 'WHITE_WON' && isWhite) || (g.result === 'BLACK_WON' && !isWhite)) outcome = 'W';
            else outcome = 'L';

            if (outcome === 'W') {
                wins++;
                runWin++;
                runLose = 0;
                maxWinStreak = Math.max(maxWinStreak, runWin);
            } else if (outcome === 'L') {
                losses++;
                runLose++;
                runWin = 0;
                maxLoseStreak = Math.max(maxLoseStreak, runLose);
            } else {
                draws++;
                runWin = 0;
                runLose = 0;
            }
        });

        for (let i = sortedGames.length - 1; i >= 0; i--) {
            const g = sortedGames[i];
            const isWhite = g.whitePlayer === targetUser;
            let outcome;
            if (g.result === 'DRAW') outcome = 'D';
            else if ((g.result === 'WHITE_WON' && isWhite) || (g.result === 'BLACK_WON' && !isWhite)) outcome = 'W';
            else outcome = 'L';
            if (i === sortedGames.length - 1) {
                if (outcome === 'W') curWinStreak = 1;
                else if (outcome === 'L') curLoseStreak = 1;
                else break;
            } else {
                if (curWinStreak > 0 && outcome === 'W') curWinStreak++;
                else if (curLoseStreak > 0 && outcome === 'L') curLoseStreak++;
                else break;
            }
        }

        return {
            total: sortedGames.length,
            wins, draws, losses,
            maxWinStreak, maxLoseStreak,
            curWinStreak, curLoseStreak,
            bestRating, worstRating,
            ratingPoints
        };
    }, [categoryGames, targetUser]);

    if (loading || !user) return <div className="text-white text-center py-10">Загрузка профиля...</div>;

    const winRate = stats.total > 0 ? Math.round((stats.wins / stats.total) * 100) : 0;

    const chartData = {
        labels: stats.ratingPoints.map((_, i) => i + 1),
        datasets: [{
            label: 'Рейтинг',
            data: stats.ratingPoints,
            borderColor: '#E8C56A',
            backgroundColor: 'rgba(255, 204, 0, 0.12)',
            pointBackgroundColor: '#6E9F4A',
            pointRadius: stats.ratingPoints.length > 40 ? 0 : 3,
            borderWidth: 2,
            tension: 0.25,
            fill: true
        }]
    };

    const chartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: { display: false },
            tooltip: { callbacks: { title: (items) => `Партия №${items[0].label}` } }
        },
        scales: {
            x: { ticks: { color: '#9A958C', maxTicksLimit: 12 }, grid: { color: '#322C24' } },
            y: { ticks: { color: '#9A958C' }, grid: { color: '#322C24' } }
        }
    };

    const viewGame = (gameId) => {
        if (!gameId) return;
        if (onViewGame) onViewGame(gameId);
        else window.location.href = `/?room=${gameId}`;
    };

    const StatCard = ({ label, value, accent }) => (
        <div className="bg-[#15130F] border border-[#322C24] rounded-lg p-3 text-center">
            <div className="text-[11px] text-[#9A958C] uppercase tracking-wide">{label}</div>
            <div className={`text-xl font-bold mt-1 ${accent || 'text-white'}`}>{value}</div>
        </div>
    );

    return (
        <div className="w-full flex flex-col gap-4 md:gap-6 max-w-[1100px] mt-3 md:mt-5">
            <div className="bg-[#201D17] border border-[#322C24] rounded-lg p-4 md:p-6 flex flex-col sm:flex-row sm:justify-between sm:items-center gap-3 shadow-lg">
                <div className="min-w-0 flex items-center gap-2.5">
                    <div className="w-10 h-10 rounded-lg bg-[#15130F] border border-[#322C24] flex items-center justify-center shrink-0">
                        <User className="w-5 h-5 text-[#E8C56A]" />
                    </div>
                    <div className="min-w-0">
                        <h1 className="text-xl md:text-2xl font-bold text-white truncate">{user.username}</h1>
                        <p className="text-xs text-[#9A958C] mt-0.5">Профиль шахматиста Agenteec Chess</p>
                    </div>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                    <button
                        onClick={shareProfile}
                        title="Скопировать ссылку на профиль"
                        className={`flex-1 sm:flex-none px-3 py-2 rounded font-bold text-sm whitespace-nowrap transition-colors inline-flex items-center justify-center gap-1.5 ${shareCopied ? 'bg-[#6E9F4A] text-white' : 'bg-[#1A1711] border border-[#6E9F4A]/50 text-[#E8C56A] hover:bg-[#2A251E]'}`}
                    >
                        {shareCopied ? <><Check className="w-4 h-4" /> Скопировано</> : <><Share2 className="w-4 h-4" /> Поделиться</>}
                    </button>
                    <button onClick={onBack} className="flex-1 sm:flex-none bg-[#2A251E] text-white px-3 py-2 rounded font-bold text-sm hover:bg-[#37322A] inline-flex items-center justify-center gap-1.5"><ArrowLeft className="w-4 h-4" /> Назад</button>
                </div>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3 md:gap-4">
                {[
                    { label: 'Bullet', rating: user.ratingBullet, games: user.gamesBullet },
                    { label: 'Blitz', rating: user.ratingBlitz, games: user.gamesBlitz },
                    { label: 'Rapid', rating: user.ratingRapid, games: user.gamesRapid },
                    { label: 'Classical', rating: user.ratingClassical, games: user.gamesClassical },
                    { label: 'Correspondence', rating: user.ratingCorrespondence, games: user.gamesCorrespondence }
                ].map((card, i) => (
                    <div key={i} className="bg-[#201D17] border border-[#322C24] rounded-lg p-4 text-center shadow">
                        <div className="font-bold text-sm text-[#E9E5DD]">{card.label}</div>
                        <div className="text-2xl font-bold text-[#E8C56A] my-1">{card.rating}{card.games < 10 ? '?' : ''}</div>
                        <div className="text-[11px] text-[#9A958C]">{card.games} партий</div>
                    </div>
                ))}
            </div>

            <div className="bg-[#201D17] border border-[#322C24] rounded-lg p-4 md:p-5 flex flex-col gap-4">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 border-b border-[#322C24] pb-2">
                    <h3 className="text-base md:text-lg font-bold text-white">Статистика</h3>
                    <div className="flex flex-wrap gap-1">
                        {[
                            { key: 'ALL', label: 'Все' },
                            { key: 'BULLET', label: 'Пуля' },
                            { key: 'BLITZ', label: 'Блиц' },
                            { key: 'RAPID', label: 'Рапид' },
                            { key: 'CLASSICAL', label: 'Классика' },
                            { key: 'CORRESPONDENCE', label: 'Заочные' }
                        ].map(c => (
                            <button
                                key={c.key}
                                onClick={() => setSelectedCategory(c.key)}
                                className={`px-2.5 py-1 text-[11px] font-bold rounded transition-colors ${selectedCategory === c.key ? 'bg-[#6E9F4A] text-white' : 'bg-[#1A1711] text-[#E9E5DD] hover:bg-[#2A251E]'}`}
                            >
                                {c.label}
                            </button>
                        ))}
                    </div>
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-2.5 md:gap-3">
                    <StatCard label="Всего партий" value={stats.total} />
                    <StatCard label="Победы" value={stats.wins} accent="text-green-400" />
                    <StatCard label="Ничьи" value={stats.draws} accent="text-[#E9E5DD]" />
                    <StatCard label="Поражения" value={stats.losses} accent="text-red-400" />
                    <StatCard label="Винрейт" value={`${winRate}%`} accent="text-[#E8C56A]" />
                    <StatCard label="Тек. серия побед" value={stats.curWinStreak} accent="text-green-400" />
                    <StatCard label="Макс. серия побед" value={stats.maxWinStreak} accent="text-green-400" />
                    <StatCard label="Тек. серия пораж." value={stats.curLoseStreak} accent="text-red-400" />
                    <StatCard label="Макс. серия пораж." value={stats.maxLoseStreak} accent="text-red-400" />
                    <StatCard label="Лучший рейтинг" value={stats.bestRating ?? '—'} accent="text-[#E8C56A]" />
                    <StatCard label="Худший рейтинг" value={stats.worstRating ?? '—'} accent="text-[#9A958C]" />
                </div>
            </div>

            <div className="bg-[#201D17] border border-[#322C24] rounded-lg p-4 md:p-5 flex flex-col gap-3">
                <h3 className="text-base md:text-lg font-bold text-white border-b border-[#322C24] pb-2">Изменение рейтинга</h3>
                {stats.ratingPoints.length === 0 ? (
                    <div className="text-center py-10 text-[#9A958C] italic">Недостаточно данных для построения графика</div>
                ) : (
                    <div className="h-[220px] md:h-[280px]">
                        <Line data={chartData} options={chartOptions} />
                    </div>
                )}
            </div>

            <div className="bg-[#201D17] border border-[#322C24] rounded-lg p-4 md:p-5 flex flex-col gap-3">
                <h3 className="text-base md:text-lg font-bold text-white border-b border-[#322C24] pb-2">История партий</h3>
                <div className="overflow-x-auto -mx-4 px-4 md:mx-0 md:px-0">
                    <table className="w-full border-collapse text-xs sm:text-sm text-left whitespace-nowrap">
                        <thead>
                        <tr className="text-white font-semibold border-b border-[#322C24]">
                            <th className="py-2.5">Дата</th>
                            <th>Соперник</th>
                            <th>Результат</th>
                            <th className="text-right">± Рейтинг</th>
                        </tr>
                        </thead>
                        <tbody>
                        {categoryGames.length === 0 ? (
                            <tr><td colSpan="4" className="text-center py-6 text-[#9A958C] italic">Партий пока нет</td></tr>
                        ) : (
                            [...categoryGames].reverse().map((g, i) => {
                                const isWhite = g.whitePlayer === targetUser;
                                const opponent = isWhite ? g.blackPlayer : g.whitePlayer;
                                let outcome, outcomeText, outcomeColor;
                                if (g.result === 'DRAW') { outcome = 'D'; outcomeText = 'Ничья'; outcomeColor = 'text-[#E9E5DD]'; }
                                else if ((g.result === 'WHITE_WON' && isWhite) || (g.result === 'BLACK_WON' && !isWhite)) { outcome = 'W'; outcomeText = 'Победа'; outcomeColor = 'text-green-400'; }
                                else { outcome = 'L'; outcomeText = 'Поражение'; outcomeColor = 'text-red-400'; }

                                const change = isWhite ? g.whiteRatingChange : g.blackRatingChange;
                                const changeText = change == null ? '—' : `${change >= 0 ? '+' : ''}${change}`;
                                const changeColor = change == null ? 'text-[#9A958C]' : (change >= 0 ? 'text-green-400' : 'text-red-400');

                                return (
                                    <tr
                                        key={g.id ?? i}
                                        onClick={() => viewGame(g.gameId)}
                                        title="Открыть просмотр партии"
                                        className="border-b border-[#1A1711] cursor-pointer hover:bg-[#1A1711] transition-colors"
                                    >
                                        <td className="py-3 text-[#9A958C] tabular-nums">{formatDate(g.endedAt)}</td>
                                        <td className="font-bold text-[#E8C56A]">{opponent || 'Anonymous'}</td>
                                        <td>
                                            <span className={`font-bold ${outcomeColor}`}>{outcomeText}</span>
                                            <span className="text-[10px] text-[#9A958C] ml-2 uppercase">{g.category || 'RAPID'}</span>
                                        </td>
                                        <td className={`text-right font-bold tabular-nums ${changeColor}`}>{changeText}</td>
                                    </tr>
                                );
                            })
                        )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
