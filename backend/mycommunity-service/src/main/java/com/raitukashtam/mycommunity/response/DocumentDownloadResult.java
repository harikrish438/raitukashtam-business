package com.raitukashtam.mycommunity.response;

/** Not returned as a JSON API response -- consumed only by the controller to build the file download response. */
public record DocumentDownloadResult(byte[] content, String contentType, String filename) {
}
