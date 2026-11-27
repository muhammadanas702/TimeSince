package com.timesince.app

/** The quotes of the Quote of the Day widget: short lines written for this app, and the picking of today's. */
object QuoteBank {

    val BUILT_IN: List<String> = listOf(
        "Small steps still move you forward.",
        "Progress, not perfection.",
        "Today is a good day to begin.",
        "Be kind to your future self: do it now.",
        "One page, one problem, one step at a time.",
        "Hard things get easier when you keep showing up.",
        "Rest is part of the plan, not a break from it.",
        "You do not have to feel ready to start.",
        "Little by little, a lot gets done.",
        "Make today a little better than yesterday.",
        "Your effort today is a gift to tomorrow.",
        "Done is better than perfect.",
        "Slow progress is still progress.",
        "Focus on the next right thing.",
        "Every expert was once a beginner.",
        "Breathe. You have handled hard days before.",
        "Choose what you want most over what you want right now.",
        "Do not wait for motivation. Build a habit.",
        "A calm mind learns faster.",
        "Start the task, and the fear shrinks.",
        "Mistakes are proof that you are trying.",
        "Drink some water, stretch, and begin again.",
        "Your pace is your own.",
        "Good things take time and patience.",
        "Be proud of how far you have come.",
        "Learn something new today.",
        "Notice one good thing today.",
        "A tidy desk, a clear mind, a first step.",
        "Try again. That is how skills are built.",
        "It is fine to take it one hour at a time.",
        "Doing it tired is still doing it.",
        "Keep the promises you make to yourself.",
        "Smile, then get back to it.",
        "Be patient with yourself. You are still growing.",
        "Show up, even when it is boring.",
        "A little effort every day beats a big effort once.",
        "Finish what is in front of you.",
        "Sleep well. Tomorrow needs you rested.",
        "You are doing better than you think.",
        "Ask the question. Someone else is wondering too."
    )

    /** "Be kind. - Anas" or "Be kind. — Anas": the text, then the author after a dash. */
    private val AUTHOR = Regex("^(.*\\S)\\s+[—–-]\\s+(\\S.{0,40})$")

    /** Splits a line the user wrote into the quote and its author (empty when there is none). */
    fun split(line: String): Pair<String, String> {
        val match = AUTHOR.find(line) ?: return Pair(line, "")
        return Pair(match.groupValues[1], match.groupValues[2])
    }

    /**
     * The quote to show on [epochDay]: one after the other, a new one each day. Each widget starts at its own
     * place in the list, and the refresh button moves [QuoteCfg.shift] on by one.
     */
    fun pick(cfg: QuoteCfg, widgetId: Int, epochDay: Long): Pair<String, String> {
        val own = if (cfg.own) cfg.ownQuotes else emptyList()
        val list = if (own.isNotEmpty()) own.map { split(it) } else BUILT_IN.map { Pair(it, "") }
        val index = (epochDay + cfg.shift + widgetId * 7L).mod(list.size.toLong()).toInt()
        return list[index]
    }
}
