package eu.depau.etchdroid

// Google Play forbids linking to pages that lead to donations: the website hides them with ?gplay=1
fun websiteUrl(path: String) = "https://etchdroid.app$path" + if (BuildConfig.PLAY_STORE) "?gplay=1" else ""

val PRIVACY_URL = websiteUrl("/privacy/")