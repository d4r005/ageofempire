package com.d4r005.ageofempire

class GameThread(private val gameView: GameView) : Thread() {
    @Volatile var running = false

    override fun run() {
        var lastNs = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var dt = (now - lastNs) / 1_000_000_000.0
            lastNs = now
            if (dt > 0.05) dt = 0.05

            gameView.updateState(dt.toFloat())

            val holder = gameView.holder
            val canvas = holder.lockCanvas()
            if (canvas == null) continue
            try {
                gameView.drawFrame(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
