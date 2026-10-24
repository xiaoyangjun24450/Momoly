package me.rerere.momoly.domain

/** Central policy for actions that must not mutate chat/configuration during generation. */
object GenerationActionGuard {
    fun allowsMutation(generating: Boolean): Boolean = !generating
}
