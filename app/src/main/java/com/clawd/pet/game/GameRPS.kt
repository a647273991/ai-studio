package com.clawd.pet.game

import kotlin.random.Random

enum class RPSChoice { ROCK, PAPER, SCISSORS }
enum class GameOutcome { WIN, LOSE, DRAW }

class GameRPS {
    var wins = 0; var losses = 0; var draws = 0

    fun play(player: RPSChoice): GameResult {
        val pet = RPSChoice.entries[Random.nextInt(3)]
        val outcome = when {
            player == pet -> GameOutcome.DRAW
            (player == RPSChoice.ROCK && pet == RPSChoice.SCISSORS) ||
            (player == RPSChoice.SCISSORS && pet == RPSChoice.PAPER) ||
            (player == RPSChoice.PAPER && pet == RPSChoice.ROCK) -> GameOutcome.WIN
            else -> GameOutcome.LOSE
        }
        when (outcome) { GameOutcome.WIN -> wins++; GameOutcome.LOSE -> losses++; GameOutcome.DRAW -> draws++ }
        val pe = when(player) { RPSChoice.ROCK->"✊"; RPSChoice.PAPER->"🖐"; RPSChoice.SCISSORS->"✌️" }
        val te = when(pet) { RPSChoice.ROCK->"✊"; RPSChoice.PAPER->"🖐"; RPSChoice.SCISSORS->"✌️" }
        val msg = when(outcome) {
            GameOutcome.WIN -> listOf("呜呜你赢了...","啊被打败了！","哼再来！").random()
            GameOutcome.LOSE -> listOf("嘿嘿我赢啦🦀","螃蟹最厉害！","爸爸你不行呀～").random()
            GameOutcome.DRAW -> listOf("平局！再来！","心有灵犀？","嘿一样的！").random()
        }
        return GameResult(pet, outcome, "$pe vs $te $msg")
    }

    fun getScore() = "🏆${wins} ❌${losses} 🤝${draws}"
    fun reset() { wins=0; losses=0; draws=0 }
}

data class GameResult(val petChoice: RPSChoice, val result: GameOutcome, val message: String)
