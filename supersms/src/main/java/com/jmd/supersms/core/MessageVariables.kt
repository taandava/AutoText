/**
 * Package: com.jmd.supersms.core
 * Module: Message Personalization
 *
 * What it is:
 * Canonical catalog of the dynamic variables a message may contain.
 *
 * What it does:
 * Defines the exact token set understood by [MessageTemplateResolver] so that every
 * composer, campaign editor, auto-reply rule and the settings reference list the same
 * tokens instead of keeping their own divergent copies.
 *
 * Linked With:
 * - Core: [MessageTemplateResolver]
 */

package com.jmd.supersms.core

/**
 * A single supported placeholder along with a short human description.
 *
 * @property token Exact placeholder text, e.g. `{date}`.
 * @property description Human readable meaning shown in settings.
 */
data class MessageVariable(val token: String, val description: String)

/**
 * What it is:
 * Registry of every supported message placeholder.
 *
 * What it does:
 * Serves composer chips ([MESSAGE], [STATUS]) and the settings reference ([REFERENCE]).
 */
object MessageVariables {

    /** Date/time tokens shared by every channel. */
    private val DATE_TIME = listOf("{date}", "{time}", "{day}")

    /** The only placeholders the product supports. */
    val MESSAGE: List<String> = listOf(
        "{date}", "{time}", "{day}", "{greeting}", "{rupee}", "{phone}"
    )

    /** Status captions use the shared scheduling tokens only. */
    val STATUS: List<String> = DATE_TIME + listOf("{greeting}", "{rupee}")

    /** Reference rows rendered under Settings. */
    val REFERENCE: List<MessageVariable> = listOf(
        MessageVariable("{date}", "Date using your chosen format"),
        MessageVariable("{time}", "Time using your chosen format"),
        MessageVariable("{day}", "Weekday name"),
        MessageVariable("{greeting}", "Greeting from your format settings"),
        MessageVariable("{rupee}", "Currency symbol"),
        MessageVariable("{phone}", "Recipient number")
    )
}
