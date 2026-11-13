package com.github.ljarka.picfort.core.network

const val WRONG_API_KEY = "Wrong API key for the Picfort server"

/** Why a call to the Picfort server failed, as the UI tells it. */
sealed class PicfortServerException(message: String) : Exception(message) {
    class NotConfigured : PicfortServerException("Connect your Picfort server first")
    class Unreachable(host: String) : PicfortServerException("Can't reach the Picfort server at $host")

    /** 401: the server didn't accept our Immich login (e.g. it uses another Immich). */
    class NotAccepted : PicfortServerException("The Picfort server didn't accept your Immich login")

    /** 404 on `/wall`: an ImageFrameServer without the wall. */
    class NoWall : PicfortServerException("This Picfort server has no Family Wall yet, update ImageFrameServer")

    /** 404 on a feature's root (`/lists`, …): a server older than that feature. */
    class Outdated(feature: String) : PicfortServerException("This Picfort server has no $feature yet, update it")

    /** 404 on one object: someone else deleted it. */
    class Gone(message: String) : PicfortServerException(message)

    /** 502: the server can't ask Immich who we are. */
    class ImmichUnreachable : PicfortServerException("The Picfort server can't reach Immich")
    class Forbidden(message: String = "Only the author can change this post") : PicfortServerException(message)

    /** 401 from the server's own PICFORT_API_KEY check, not from Immich. */
    class WrongApiKey : PicfortServerException(WRONG_API_KEY)
    class Other(message: String) : PicfortServerException(message)
}
