const _timeStrToNumber = (t) => {
    var e = t.split(':');
    if (3 === e.length && 2 === e[1].length && 2 === e[2].length) {
        var i = parseInt(e[0]),
            o = parseInt(e[1]),
            a = parseInt(e[2]);
        if (o >= 0 && o < 60 && a >= 0 && a < 60) return a + 60 * o + 3600 * i
    }
    if (2 === e.length && 2 === e[1].length) {
        var o = parseInt(e[0]),
            a = parseInt(e[1]);
        if (a >= 0 && a < 60) return a + 60 * o
    }
}

const getVideoTime = () => _timeStrToNumber(document.getElementsByClassName("bilibili-player-video-time-now")[0].innerHTML)
const setVideoTime = (sec, part) => window.commentAgent.seek(sec, part ? part : -1)
const videoHasPart = () => document.getElementsByClassName("list-box").length > 0
const getVideoPart = () => Array.from(document.getElementsByClassName("list-box")[0].childNodes).findIndex(e => e.classList.contains("on")) + 1;

const setVideoLoop = (startSec, endSec, part) => {
    setVideoTime(startSec, part)
    var startPart = part
    setTimeout(() => {
        var interval = setInterval(() => {
            var checkTime = getVideoTime()
            if (videoHasPart() && getVideoPart() !== startPart) {
                console.log("cleared switching parts")
                clearInterval(interval)
                return
            } else if (checkTime > endSec) {
                setVideoTime(startSec)
            } else if (checkTime < startSec || checkTime > endSec + 2) {
                console.log("cleared for leaving duration")
                clearInterval(interval)
            }
        }, 100)
    }, 2000)
}

const setVideoLoopHelper = (start, end, part) => setVideoLoop(_timeStrToNumber(start), _timeStrToNumber(end), part)

// 复制以上内容至console并运行
// 通过以下格式使用

setVideoLoopHelper("31:49", "32:33", 2) // av85179902
