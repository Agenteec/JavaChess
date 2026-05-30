import React, { useState, useEffect, useRef } from 'react';
import { Chessboard } from 'react-chessboard';
import { Chess } from 'chess.js';

const START_FEN = new Chess().fen();

const getInitials = (name) => {
    if (!name) return '?';
    const clean = name.replace(/^Guest_?/i, 'G');
    return clean.slice(0, 2).toUpperCase();
};

const isWaitingName = (name) => !name || /ожидан/i.test(name) || name === 'null';

export default function ChessGame({ roomId, username, onExit, initialMins, initialInc }) {
    const [game, setGame] = useState(new Chess());
    const [fen, setFen] = useState(START_FEN);
    const [boardOrientation, setBoardOrientation] = useState('white');
    const [playerColor, setPlayerColor] = useState('SPECTATOR');

    const [whitePlayer, setWhitePlayer] = useState('Ожидание соперника...');
    const [blackPlayer, setBlackPlayer] = useState('Ожидание соперника...');
    const [whiteRating, setWhiteRating] = useState(1500);
    const [blackRating, setBlackRating] = useState(1500);

    const [whiteTime, setWhiteTime] = useState(600);
    const [blackTime, setBlackTime] = useState(600);
    const [currentTurn, setCurrentTurn] = useState('WHITE');
    const [timeCategory, setTimeCategory] = useState('RAPID');

    const [selectedSquare, setSelectedSquare] = useState(null);
    const [customSquareStyles, setCustomSquareStyles] = useState({});

    const [historyMoves, setHistoryMoves] = useState([]);
    const [currentHistoryIndex, setCurrentHistoryIndex] = useState(-1);
    const [chatMessages, setChatMessages] = useState([]);
    const [chatInput, setChatInput] = useState('');

    const [isChatOpen, setIsChatOpen] = useState(false);
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const [showDrawOfferAlert, setShowDrawOfferAlert] = useState(false);
    const [drawOfferSender, setDrawOfferSender] = useState('');

    const [gameOverData, setGameOverData] = useState(null);

    const socketRef = useRef(null);
    const timerIntervalRef = useRef(null);
    const chatEndRef = useRef(null);

    const historyIndexRef = useRef(-1);
    const timeSyncRef = useRef({ white: 600, black: 600, turn: 'WHITE', at: Date.now() });
    const timeoutFiredRef = useRef(false);

    const syncHistoryIndex = (idx) => {
        historyIndexRef.current = idx;
        setCurrentHistoryIndex(idx);
    };

    useEffect(() => {
        const wsProtocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
        const wsUrl = `${wsProtocol}${window.location.host}/game`;

        const socket = new WebSocket(wsUrl);
        socketRef.current = socket;

        socket.onopen = () => {
            socket.send(JSON.stringify({
                action: "JOIN",
                gameId: roomId,
                username,
                minutes: initialMins ? parseInt(initialMins, 10) : undefined,
                increment: initialInc ? parseInt(initialInc, 10) : undefined
            }));
        };

        socket.onmessage = (event) => {
            const data = JSON.parse(event.data);

            if (data.type === "STATE") {
                if (data.role) {
                    setPlayerColor(data.role);
                    setBoardOrientation(data.role === 'BLACK' ? 'black' : 'white');
                }

                if (data.whitePlayer) setWhitePlayer(data.whitePlayer);
                if (data.blackPlayer) setBlackPlayer(data.blackPlayer);
                if (data.whiteRating) setWhiteRating(data.whiteRating);
                if (data.blackRating) setBlackRating(data.blackRating);
                if (data.category) setTimeCategory(data.category);

                const tempGame = new Chess();
                if (data.moves) {
                    data.moves.forEach(mStr => {
                        try {
                            tempGame.move({
                                from: mStr.substring(0, 2).toLowerCase(),
                                to: mStr.substring(2, 4).toLowerCase(),
                                promotion: 'q'
                            });
                        } catch (e) {}
                    });
                }
                setGame(tempGame);
                setHistoryMoves(tempGame.history({ verbose: true }));

                if (historyIndexRef.current === -1) {
                    setFen(tempGame.fen());
                }

                const wt = typeof data.whiteTimeLeft === 'number' ? data.whiteTimeLeft : whiteTime;
                const bt = typeof data.blackTimeLeft === 'number' ? data.blackTimeLeft : blackTime;
                timeSyncRef.current = { white: wt, black: bt, turn: data.turn, at: Date.now() };
                timeoutFiredRef.current = false;
                setWhiteTime(wt);
                setBlackTime(bt);
                setCurrentTurn(data.turn);

                if (data.mated || data.draw || data.lastMove === "TIMEOUT" || data.lastMove === "ABORTED") {
                    handleGameOverEvent(data);
                }
            }
            else if (data.type === "DRAW_OFFER_RECEIVED") {
                if (data.sender !== username) {
                    setShowDrawOfferAlert(true);
                    setDrawOfferSender(data.sender);
                }
            }
            else if (data.type === "DRAW_DECLINED") {
                setShowDrawOfferAlert(false);
                setChatMessages(prev => [...prev, { sender: 'System', message: '❌ Оппонент отклонил предложение ничьей.' }]);
            }
            else if (data.type === "CHAT") {
                setChatMessages(prev => [...prev, { sender: data.sender, message: data.message }]);
            }
        };

        return () => {
            socket.close();
            if (timerIntervalRef.current) clearInterval(timerIntervalRef.current);
        };
    }, [roomId, username]);

    useEffect(() => {
        if (chatEndRef.current) {
            chatEndRef.current.scrollIntoView({ behavior: 'smooth' });
        }
    }, [chatMessages, isChatOpen]);

    const bothPlayersPresent = !isWaitingName(whitePlayer) && !isWaitingName(blackPlayer);
    const isGameActive = bothPlayersPresent && !gameOverData;

    useEffect(() => {
        if (!isGameActive) return;

        timerIntervalRef.current = setInterval(() => {
            const { white, black, turn, at } = timeSyncRef.current;
            const elapsed = Math.max(0, Math.floor((Date.now() - at) / 1000));

            if (turn === 'WHITE') {
                const left = Math.max(0, white - elapsed);
                setWhiteTime(left);
                setBlackTime(black);
                if (left === 0) fireTimeoutOnce();
            } else {
                const left = Math.max(0, black - elapsed);
                setBlackTime(left);
                setWhiteTime(white);
                if (left === 0) fireTimeoutOnce();
            }
        }, 250);

        return () => clearInterval(timerIntervalRef.current);
    }, [isGameActive]);

    useEffect(() => {
        const handleKeyDown = (e) => {
            if (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA') {
                return;
            }
            if (e.key === 'ArrowLeft') navPrevMove();
            else if (e.key === 'ArrowRight') navNextMove();
            else if (e.key === 'ArrowUp') navLastMove();
            else if (e.key === 'ArrowDown') navFirstMove();
        };
        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [historyMoves, currentHistoryIndex]);

    const fireTimeoutOnce = () => {
        if (timeoutFiredRef.current) return;
        timeoutFiredRef.current = true;
        triggerTimeout();
    };

    const triggerTimeout = () => {
        if (socketRef.current && socketRef.current.readyState === WebSocket.OPEN) {
            socketRef.current.send(JSON.stringify({ action: "TIMEOUT", gameId: roomId, username }));
        }
    };

    const handleGameOverEvent = (data) => {
        const whiteChange = data.whiteRatingChange || 0;
        const blackChange = data.blackRatingChange || 0;
        const whiteNew = data.whiteNewRating || whiteRating;
        const blackNew = data.blackNewRating || blackRating;

        setGameOverData({
            mated: data.mated,
            draw: data.draw,
            endReason: data.endReason,
            turn: data.turn,
            whiteChange,
            blackChange,
            whiteNew,
            blackNew
        });
    };

    const canDragPiece = ({ square }) => {
        if (gameOverData || playerColor === 'SPECTATOR') return false;
        if (historyIndexRef.current !== -1) return false;
        const piece = game.get(square);
        if (!piece) return false;
        if (playerColor === 'WHITE' && piece.color !== 'w') return false;
        if (playerColor === 'BLACK' && piece.color !== 'b') return false;
        const isMyTurn = (game.turn() === 'w' && playerColor === 'WHITE') ||
            (game.turn() === 'b' && playerColor === 'BLACK');
        return isMyTurn;
    };

    const handleMove = (source, target) => {
        if (!target) return false;
        const newGame = new Chess(game.fen());
        try {
            const move = newGame.move({ from: source, to: target, promotion: 'q' });
            if (move === null) return false;

            socketRef.current.send(JSON.stringify({
                action: "MOVE", gameId: roomId, username, from: source, to: target
            }));

            setGame(newGame);
            setFen(newGame.fen());
            setCustomSquareStyles({});
            setSelectedSquare(null);
            return true;
        } catch (e) {
            return false;
        }
    };

    const onSquareClick = (square) => {
        if (game.isGameOver() || playerColor === 'SPECTATOR' || gameOverData) return;
        if (historyIndexRef.current !== -1) return;

        const isMyTurn = (game.turn() === 'w' && playerColor === 'WHITE') || (game.turn() === 'b' && playerColor === 'BLACK');
        if (!isMyTurn) return;

        const piece = game.get(square);

        if (piece && ((game.turn() === 'w' && piece.color === 'w') || (game.turn() === 'b' && piece.color === 'b'))) {
            setSelectedSquare(square);

            const moves = game.moves({ square: square, verbose: true });
            const styles = {};

            styles[square] = { background: 'rgba(255, 204, 0, 0.45)' };

            moves.forEach(m => {
                const targetPiece = game.get(m.to);
                styles[m.to] = targetPiece
                    ? { background: 'radial-gradient(circle, transparent 70%, rgba(255,204,0,0.55) 70%)' }
                    : { background: 'radial-gradient(circle, rgba(255,255,255,0.30) 22%, transparent 22%)' };
            });
            setCustomSquareStyles(styles);
        } else if (selectedSquare) {
            const ok = handleMove(selectedSquare, square);
            if (!ok) {
                setCustomSquareStyles({});
                setSelectedSquare(null);
            }
        }
    };

    const getFenOfMove = (index) => {
        const tempGame = new Chess();
        for (let i = 0; i <= index; i++) {
            tempGame.move({
                from: historyMoves[i].from,
                to: historyMoves[i].to,
                promotion: historyMoves[i].promotion || 'q'
            });
        }
        return tempGame.fen();
    };

    const jumpToHistoryMove = (index) => {
        syncHistoryIndex(index);
        if (index === -1) {
            setFen(game.fen());
        } else if (index === -2) {
            setFen(START_FEN);
        } else {
            setFen(getFenOfMove(index));
        }
    };

    const navFirstMove = () => { if (historyMoves.length > 0) jumpToHistoryMove(-2); };
    const navPrevMove = () => {
        if (historyMoves.length === 0) return;
        let target = currentHistoryIndex === -1 ? historyMoves.length - 1 : currentHistoryIndex;
        if (target === -2) return;
        if (target === 0) jumpToHistoryMove(-2);
        else jumpToHistoryMove(target - 1);
    };
    const navNextMove = () => {
        if (historyMoves.length === 0) return;
        if (currentHistoryIndex === -2) { jumpToHistoryMove(0); return; }
        if (currentHistoryIndex === -1 || currentHistoryIndex === historyMoves.length - 1) return;
        jumpToHistoryMove(currentHistoryIndex + 1);
    };
    const navLastMove = () => { if (historyMoves.length > 0) jumpToHistoryMove(-1); };

    const surrenderGame = () => {
        if (confirm("Вы действительно хотите сдаться? Вашему сопернику будет присуждена победа.")) {
            socketRef.current.send(JSON.stringify({ action: "SURRENDER", gameId: roomId, username }));
        }
    };

    const offerDrawGame = () => {
        if (confirm("Предложить сопернику ничью?")) {
            socketRef.current.send(JSON.stringify({ action: "DRAW_OFFER", gameId: roomId, username }));
        }
    };

    const acceptDrawOffer = () => {
        socketRef.current.send(JSON.stringify({ action: "DRAW_ACCEPT", gameId: roomId, username }));
        cleanupDrawOfferUI();
    };

    const declineDrawOffer = () => {
        socketRef.current.send(JSON.stringify({ action: "DRAW_DECLINE", gameId: roomId, username }));
        cleanupDrawOfferUI();
    };

    const cleanupDrawOfferUI = () => {
        setShowDrawOfferAlert(false);
        setIsMenuOpen(false);
    };

    const sendChatMessage = () => {
        const text = chatInput.trim();
        if (!text) return;
        const sock = socketRef.current;
        if (!sock || sock.readyState !== WebSocket.OPEN) {
            setChatMessages(prev => [...prev, { sender: 'System', message: '⚠ Нет соединения, сообщение не отправлено.' }]);
            return;
        }
        sock.send(JSON.stringify({ action: "CHAT", gameId: roomId, username, message: text }));
        setChatInput('');
    };

    const toggleMobileMenu = () => {
        setIsMenuOpen(prev => !prev);
    };

    const formatTime = (seconds) => {
        const safe = Math.max(0, Math.floor(seconds || 0));
        const mins = Math.floor(safe / 60);
        const secs = safe % 60;
        return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    };

    const isBottomWhite = playerColor === 'WHITE' || (playerColor === 'SPECTATOR' && boardOrientation === 'white');

    const topName = isBottomWhite ? blackPlayer : whitePlayer;
    const topTime = isBottomWhite ? blackTime : whiteTime;
    const topRating = isBottomWhite ? blackRating : whiteRating;
    const topColor = isBottomWhite ? 'BLACK' : 'WHITE';

    const bottomName = isBottomWhite ? whitePlayer : blackPlayer;
    const bottomTime = isBottomWhite ? whiteTime : blackTime;
    const bottomRating = isBottomWhite ? whiteRating : blackRating;
    const bottomColor = isBottomWhite ? 'WHITE' : 'BLACK';

    const lowTimeThreshold = (() => {
        switch ((timeCategory || 'RAPID').toUpperCase()) {
            case 'BULLET': return 15;
            case 'BLITZ': return 30;
            case 'CLASSICAL': return 90;
            case 'CORRESPONDENCE': return 0;
            default: return 60;
        }
    })();

    const topActive = isGameActive && currentTurn === topColor;
    const bottomActive = isGameActive && currentTurn === bottomColor;

    const chessboardOptions = {
        id: 'main-board',
        position: fen,
        boardOrientation,
        onPieceDrop: ({ sourceSquare, targetSquare }) => handleMove(sourceSquare, targetSquare),
        onSquareClick: ({ square }) => onSquareClick(square),
        canDragPiece,
        squareStyles: customSquareStyles,
        darkSquareStyle: { backgroundColor: '#b58863' },
        lightSquareStyle: { backgroundColor: '#f0d9b5' },
        boardStyle: { borderRadius: '6px', boxShadow: '0 10px 34px rgba(0, 0, 0, 0.55)' },
        allowDrawingArrows: true,
        animationDurationInMs: 200
    };

    const PlayerBar = ({ name, rating, time, isActive, colorSymbol, position }) => {
        const waiting = isWaitingName(name);
        const lowTime = isActive && lowTimeThreshold > 0 && time <= lowTimeThreshold;
        return (
            <div className={`w-full max-w-[460px] flex justify-between items-center gap-3 px-3 py-2.5 rounded-xl border transition-colors duration-300
                ${isActive ? 'bg-[#24221d] border-[#ffcc00]/50' : 'bg-[#1e1c18] border-[#2a2824]'}
                ${position === 'top' ? 'mb-2' : 'mt-2.5'}`}>
                <div className="flex items-center gap-2.5 min-w-0">
                    <div className={`flex items-center justify-center w-9 h-9 rounded-lg text-sm font-extrabold shrink-0
                        ${colorSymbol === '⚪' ? 'bg-[#f0d9b5] text-[#3a2a1a]' : 'bg-[#3a3833] text-[#f0d9b5] border border-[#55524c]'}`}>
                        {waiting ? '…' : getInitials(name)}
                    </div>
                    <span className="font-bold text-white text-sm md:text-base truncate">
                        {waiting ? <span className="text-[#8b8985] font-medium italic">Ожидание соперника…</span> : (
                            <>{name} <span className="text-xs font-semibold text-[#8b8985]">({rating})</span></>
                        )}
                    </span>
                </div>
                <span className={`px-3 py-1 rounded-lg text-lg md:text-xl font-bold tabular-nums border transition-all duration-200 shrink-0
                    ${lowTime ? 'border-red-500 bg-red-500/15 text-red-300 clock-low'
                              : isActive ? 'border-[#ffcc00] bg-[#ffcc00]/10 text-[#ffe27a]'
                                         : 'border-transparent bg-[#161512]/60 text-[#cfcdc8]'}`}>
                    {formatTime(time)}
                </span>
            </div>
        );
    };

    return (
        <main className="w-full max-w-[1200px] flex flex-col md:flex-row gap-5 p-2.5 md:p-5 box-border h-auto md:h-[calc(100vh-64px)] overflow-hidden">

            <div className="flex-[1.3] flex flex-col items-center justify-center md:justify-start w-full">

                <div className="md:hidden w-full max-w-[460px] bg-[#161512] border border-[#2a2824] rounded-lg px-3 py-2 mb-2 overflow-x-auto whitespace-nowrap text-sm leading-relaxed">
                    {historyMoves.length === 0 ? (
                        <span className="text-[#8b8985] text-xs">Ходов еще не сделано</span>
                    ) : (
                        historyMoves.map((m, i) => (
                            <span key={i} className="inline-flex gap-1 mr-3">
                                {i % 2 === 0 && <span className="text-[#8b8985] font-bold">{Math.floor(i / 2) + 1}.</span>}
                                <span onClick={() => jumpToHistoryMove(i)} className={`cursor-pointer px-1 rounded hover:bg-[#363431] ${currentHistoryIndex === i ? 'bg-[#577d36] text-white' : 'text-white'}`}>{m.san}</span>
                            </span>
                        ))
                    )}
                </div>

                <PlayerBar name={topName} rating={topRating} time={topTime} isActive={topActive} colorSymbol={topColor === 'WHITE' ? '⚪' : '⚫'} position="top" />

                <div id="board" className="w-full max-w-[460px]">
                    <Chessboard options={chessboardOptions} />
                </div>

                <PlayerBar name={bottomName} rating={bottomRating} time={bottomTime} isActive={bottomActive} colorSymbol={bottomColor === 'WHITE' ? '⚪' : '⚫'} position="bottom" />

                <div className="text-sm font-bold text-[#f0d9b5] mt-3 animate-fade-in" id="gameStatus">
                    {gameOverData ? (
                        gameOverData.endReason === "ABORTED" ? "ПАРТИЯ ОТМЕНЕНА (истекло время первого хода)" : "ИГРА ЗАВЕРШЕНА"
                    ) : !isGameActive ? "Ожидание соперника…" : ""}
                </div>

                <div className="md:hidden flex w-full max-w-[460px] justify-between gap-2.5 mt-3">
                    <button onClick={() => setIsChatOpen(true)} className="flex-1 bg-[#363431] text-white p-2.5 rounded-lg font-bold text-sm hover:bg-[#45433f] transition-colors">Чат</button>
                    <button id="mobileMenuToggleBtn" onClick={toggleMobileMenu} className={`flex-1 bg-[#363431] text-white p-2.5 rounded-lg font-bold text-sm transition-colors hover:bg-[#45433f] ${showDrawOfferAlert ? 'pulse-active' : ''}`}>Меню</button>
                    <button onClick={navPrevMove} className="bg-[#363431] text-white py-2.5 px-4 rounded-lg font-bold hover:bg-[#45433f] transition-colors">◀</button>
                    <button onClick={navNextMove} className="bg-[#363431] text-white py-2.5 px-4 rounded-lg font-bold hover:bg-[#45433f] transition-colors">▶</button>
                </div>
            </div>

            <div className="hidden md:flex flex-col gap-3 w-full max-h-full flex-1">

                {gameOverData && (
                    <div className="bg-[#22201c] border-2 border-[#577d36] rounded-xl p-4 flex flex-col gap-2.5 shadow-2xl animate-fade-in">
                        <h3 className="text-lg font-bold text-yellow-500 text-center">
                            {gameOverData.endReason === "ABORTED" && "Партия отменена"}
                            {gameOverData.endReason === "MATE" && `1-0 Победили ${gameOverData.turn === "BLACK" ? 'Белые' : 'Черные'}`}
                            {gameOverData.endReason === "TIMEOUT" && `0-1 Победили ${whiteTime <= 0 ? 'Черные' : 'Белые'}`}
                            {gameOverData.endReason === "SURRENDER" && `1-0 Победили ${currentTurn === "WHITE" ? 'Черные' : 'Белые'}`}
                            {gameOverData.endReason === "DRAW" && `½-½ Ничья`}
                        </h3>
                        <p className="text-xs text-[#bababa] text-center leading-relaxed">
                            {gameOverData.endReason === "ABORTED" && "Игра прервана, так как первый ход не был совершен вовремя. Изменений рейтинга нет."}
                            {gameOverData.endReason === "MATE" && "Мат на доске. Король соперника атакован и не имеет защиты."}
                            {gameOverData.endReason === "TIMEOUT" && "Победа по времени (таймаут)."}
                            {gameOverData.endReason === "SURRENDER" && "Техническая победа. Оппонент признал свое поражение."}
                            {gameOverData.endReason === "DRAW" && "Пат или соглашение сторон."}
                        </p>
                        <div className="bg-[#161512] rounded-lg p-2.5 border border-[#2a2824] flex flex-col gap-2">
                            <div className="flex justify-between text-xs font-bold text-white">
                                <span>{whitePlayer} ({gameOverData.whiteNew})</span>
                                <span className={gameOverData.whiteChange >= 0 ? "text-green-400" : "text-red-400"}>({gameOverData.whiteChange >= 0 ? "+" : ""}{gameOverData.whiteChange})</span>
                            </div>
                            <div className="flex justify-between text-xs font-bold text-white">
                                <span>{blackPlayer} ({gameOverData.blackNew})</span>
                                <span className={gameOverData.blackChange >= 0 ? "text-green-400" : "text-red-400"}>({gameOverData.blackChange >= 0 ? "+" : ""}{gameOverData.blackChange})</span>
                            </div>
                        </div>
                        <button onClick={onExit} className="bg-[#577d36] text-white p-2 rounded-lg font-bold hover:bg-[#689243] text-sm transition-colors">В лобби ➜</button>
                    </div>
                )}

                {!gameOverData && (
                    <div className="bg-[#1e1c18] border border-[#2a2824] rounded-xl p-3 flex flex-col gap-2 shadow-md">
                        {showDrawOfferAlert ? (
                            <div className="flex flex-col gap-2 animate-fade-in">
                                <span className="text-yellow-500 text-xs font-bold text-center">Оппонент предлагает ничью</span>
                                <div className="flex gap-2">
                                    <button onClick={acceptDrawOffer} className="flex-1 bg-green-600 text-white p-2 rounded-lg font-bold text-xs hover:bg-green-700 transition-colors">Принять</button>
                                    <button onClick={declineDrawOffer} className="flex-1 bg-red-600 text-white p-2 rounded-lg font-bold text-xs hover:bg-red-700 transition-colors">Отклонить</button>
                                </div>
                            </div>
                        ) : (
                            <div className="flex gap-2">
                                <button onClick={offerDrawGame} className="flex-1 bg-[#262421] border border-[#f0d9b5]/30 text-[#f0d9b5] p-2.5 rounded-lg font-bold text-xs hover:bg-[#363431] hover:border-[#f0d9b5]/60 transition-colors">Предложить ничью</button>
                                <button id="surrenderBtn" onClick={surrenderGame} className="flex-1 bg-[#262421] border border-red-500/40 text-red-400 p-2.5 rounded-lg font-bold text-xs hover:bg-red-500/10 hover:border-red-500 transition-colors">Сдаться</button>
                            </div>
                        )}
                    </div>
                )}

                <div className="bg-[#1e1c18] border border-[#2a2824] rounded-xl p-4 flex flex-col flex-1 min-h-0">
                    <h4 className="text-sm font-bold text-white m-0 mb-2">История ходов</h4>
                    <div id="desktopNotation" className="flex-1 overflow-y-auto bg-[#161512] border border-[#2a2824] rounded-lg p-1 min-h-0 text-[13px] leading-none">
                        {historyMoves.length === 0 ? (
                            <div className="text-center py-5 text-xs text-[#8b8985]">Ходов еще не сделано</div>
                        ) : (
                            Array.from({ length: Math.ceil(historyMoves.length / 2) }).map((_, row) => {
                                const wi = row * 2, bi = row * 2 + 1;
                                const w = historyMoves[wi], b = historyMoves[bi];
                                const cell = (idx, san) => (
                                    <span onClick={() => jumpToHistoryMove(idx)}
                                        className={`flex-1 cursor-pointer px-1.5 py-1 rounded transition-colors ${currentHistoryIndex === idx ? 'bg-[#577d36] text-white font-semibold' : 'text-[#e8e6e1] hover:bg-[#2a2824]'}`}>
                                        {san}
                                    </span>
                                );
                                return (
                                    <div key={row} className="flex items-center gap-1 odd:bg-[#1b1a17] rounded-sm">
                                        <span className="w-7 text-right pr-1 text-[#6f6d68] tabular-nums shrink-0 select-none">{row + 1}.</span>
                                        {w ? cell(wi, w.san) : <span className="flex-1" />}
                                        {b ? cell(bi, b.san) : <span className="flex-1" />}
                                    </div>
                                );
                            })
                        )}
                    </div>
                    <div className="flex gap-1.5 mt-3">
                        <button onClick={navFirstMove} className="flex-1 bg-[#363431] text-white p-1.5 rounded-lg hover:bg-[#45433f] text-xs transition-colors">⏮</button>
                        <button onClick={navPrevMove} className="flex-1 bg-[#363431] text-white p-1.5 rounded-lg hover:bg-[#45433f] text-xs transition-colors">◀</button>
                        <button onClick={navNextMove} className="flex-1 bg-[#363431] text-white p-1.5 rounded-lg hover:bg-[#45433f] text-xs transition-colors">▶</button>
                        <button onClick={navLastMove} className="flex-1 bg-[#363431] text-white p-1.5 rounded-lg hover:bg-[#45433f] text-xs transition-colors">⏭</button>
                    </div>
                </div>

                <div className="bg-[#1e1c18] border border-[#2a2824] rounded-xl p-4 flex flex-col flex-1 min-h-0 gap-2">
                    <h4 className="text-sm font-bold text-white m-0">Чат</h4>
                    <div id="chat" className="flex-1 overflow-y-auto bg-[#161512] border border-[#2a2824] rounded-lg p-2 text-xs">
                        {chatMessages.map((msg, i) => (
                            <div key={i} className="mb-1.5">
                                <span className="text-[#f0d9b5] font-bold">{msg.sender}:</span> {msg.message}
                            </div>
                        ))}
                        <div ref={chatEndRef}></div>
                    </div>
                    <div className="flex gap-1.5">
                        <input type="text" value={chatInput} onChange={e => setChatInput(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); sendChatMessage(); } }} placeholder="Введите сообщение..." className="flex-1 bg-[#161512] border border-[#2a2824] text-white p-2 text-xs rounded-lg focus:border-[#577d36] outline-none transition-colors" />
                        <button onClick={sendChatMessage} className="bg-[#577d36] text-white px-3 py-2 rounded-lg text-xs hover:bg-[#689243] transition-colors">➜</button>
                    </div>
                </div>
            </div>


            {isChatOpen && (
                <div id="chatContainer" className="fixed inset-0 bg-[#161512]/98 z-[2000] flex flex-col p-5 box-border">
                    <div className="flex justify-between items-center border-b border-[#2a2824] pb-2 mb-3">
                        <h4 className="text-base font-bold text-white m-0">Чат партии</h4>
                        <span className="cursor-pointer text-3xl font-bold text-[#8b8985]" onClick={() => setIsChatOpen(false)}>×</span>
                    </div>
                    <div id="chat" className="flex-1 overflow-y-auto bg-[#161512] border border-[#2a2824] rounded-lg p-3 text-sm mb-3">
                        {chatMessages.map((msg, i) => (
                            <div key={i} className="mb-2">
                                <span className="text-[#f0d9b5] font-bold">{msg.sender}:</span> {msg.message}
                            </div>
                        ))}
                        <div ref={chatEndRef}></div>
                    </div>
                    <div className="flex gap-2">
                        <input type="text" value={chatInput} onChange={e => setChatInput(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); sendChatMessage(); } }} placeholder="Введите сообщение..." className="flex-1 bg-[#1e1c18] border border-[#2a2824] text-white p-3 rounded-lg focus:border-[#577d36] outline-none" />
                        <button onClick={sendChatMessage} className="bg-[#577d36] text-white px-5 py-3 rounded-lg">➜</button>
                    </div>
                </div>
            )}

            {isMenuOpen && (
                <div className="fixed inset-0 bg-black/85 backdrop-blur-sm flex justify-center items-center z-[1500] p-4" onClick={toggleMobileMenu}>
                    <div className="bg-[#1e1c18] border border-[#2a2824] rounded-xl p-6 max-w-[380px] w-full flex flex-col gap-4 shadow-2xl" onClick={e => e.stopPropagation()}>
                        <div className="flex justify-between items-center border-b border-[#2a2824] pb-2">
                            <h3 className="text-lg font-bold text-white">Меню игры</h3>
                            <span className="cursor-pointer text-2xl text-[#8b8985] hover:text-white" onClick={toggleMobileMenu}>×</span>
                        </div>

                        {gameOverData && (
                            <div className="bg-[#22201c] border border-[#577d36] rounded-lg p-3 flex flex-col gap-2">
                                <h4 className="text-base font-bold text-yellow-500 text-center m-0">
                                    {gameOverData.endReason === "ABORTED" && "Партия отменена"}
                                    {gameOverData.endReason === "MATE" && `1-0 Победили ${gameOverData.turn === "BLACK" ? 'Белые' : 'Черные'}`}
                                    {gameOverData.endReason === "TIMEOUT" && `0-1 Победили ${whiteTime <= 0 ? 'Черные' : 'Белые'}`}
                                    {gameOverData.endReason === "SURRENDER" && `1-0 Победили ${currentTurn === "WHITE" ? 'Черные' : 'Белые'}`}
                                    {gameOverData.endReason === "DRAW" && `½-½ Ничья`}
                                </h4>
                                <div className="text-[11px] text-[#8b8985] text-center mb-1">
                                    {gameOverData.endReason === "ABORTED" && "Первый ход не был сделан вовремя."}
                                    {gameOverData.endReason === "MATE" && "Мат на доске."}
                                    {gameOverData.endReason === "TIMEOUT" && "Победа по времени (таймаут)."}
                                    {gameOverData.endReason === "SURRENDER" && "Оппонент признал поражение."}
                                    {gameOverData.endReason === "DRAW" && "Пат или соглашение сторон."}
                                </div>
                                <div className="bg-[#161512] rounded-lg p-2 text-xs flex flex-col gap-1 text-white font-semibold">
                                    <div className="flex justify-between">
                                        <span>{whitePlayer} ({gameOverData.whiteNew})</span>
                                        <span className={gameOverData.whiteChange >= 0 ? "text-green-400" : "text-red-400"}>({gameOverData.whiteChange >= 0 ? "+" : ""}{gameOverData.whiteChange})</span>
                                    </div>
                                    <div className="flex justify-between">
                                        <span>{blackPlayer} ({gameOverData.blackNew})</span>
                                        <span className={gameOverData.blackChange >= 0 ? "text-green-400" : "text-red-400"}>({gameOverData.blackChange >= 0 ? "+" : ""}{gameOverData.blackChange})</span>
                                    </div>
                                </div>
                                <button onClick={onExit} className="bg-[#577d36] text-white p-2.5 rounded-lg font-bold text-sm w-full mt-1">Вернуться в лобби ➜</button>
                            </div>
                        )}

                        {showDrawOfferAlert && (
                            <div className="flex flex-col gap-2">
                                <div className="text-yellow-500 text-sm font-bold text-center">Оппонент {drawOfferSender} предлагает ничью:</div>
                                <button onClick={acceptDrawOffer} className="w-full bg-green-600 text-white p-2.5 rounded-lg font-bold text-sm">Принять ничью</button>
                                <button onClick={declineDrawOffer} className="w-full bg-red-600 text-white p-2.5 rounded-lg font-bold text-sm">Отклонить</button>
                            </div>
                        )}

                        {!gameOverData && !showDrawOfferAlert && (
                            <div className="flex flex-col gap-2.5 mt-2">
                                <button onClick={offerDrawGame} className="w-full bg-[#262421] border border-[#f0d9b5]/30 text-[#f0d9b5] p-3 rounded-lg font-bold text-sm hover:bg-[#363431] hover:border-[#f0d9b5]/60 transition-colors">Предложить ничью</button>
                                <button onClick={surrenderGame} className="w-full bg-[#262421] border border-red-500/40 text-red-400 p-3 rounded-lg font-bold text-sm hover:bg-red-500/10 hover:border-red-500 transition-colors">Сдаться</button>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </main>
    );
}
