package com.chat360.chatbot.network.rest.thirdparty

import java.io.IOException

/** A non-2xx HTTP response from the `third-party-tasks` API - [httpCode] lets callers (e.g.
 * [com.chat360.chatbot.domain.thirdparty.ChatHistoryRepository]'s 401-retry-once logic) branch on
 * the real status code instead of string-matching the exception message. */
class ThirdPartyHttpException(val httpCode: Int, url: String) : IOException("HTTP $httpCode for $url")

/** A 2xx response whose `{success, data}` envelope had no `data` - distinct from
 * [ThirdPartyHttpException] since it's not an HTTP-level failure and should never be mistaken
 * for one (e.g. never satisfies a `== 401` check). */
class ThirdPartyMalformedResponseException(endpoint: String) :
    IOException("third-party-tasks $endpoint returned a successful response with no data")
