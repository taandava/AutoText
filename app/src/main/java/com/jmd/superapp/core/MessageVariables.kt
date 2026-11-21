/**
 * Package: com.jmd.superapp.core
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

package com.jmd.superapp.core

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
        "{name}", "{date}", "{time}", "{day}", "{greeting}", "{rupee}", "{phone}"
    )

    /** Status captions use the shared scheduling tokens only. */
    val STATUS: List<String> = listOf("{date}", "{time}", "{day}", "{greeting}", "{rupee}")

    /** Reference rows rendered under Settings. */
    val REFERENCE: List<MessageVariable> = listOf(
        MessageVariable("{name}", "Recipient contact name"),
        MessageVariable("{phone}", "Recipient phone number"),
        MessageVariable("{greeting}", "Greeting based on settings"),
        MessageVariable("{rupee}", "Currency symbol"),
        MessageVariable("{date}", "Date using chosen format"),
        MessageVariable("{time}", "Time using chosen format"),
        MessageVariable("{day}", "Weekday name")
    )
}
