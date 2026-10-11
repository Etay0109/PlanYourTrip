package com.example.planyourtrip

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

/** Disk cache of the destination photos: shown offline after their first load. */
private const val PHOTO_DISK_CACHE_BYTES = 50L * 1024 * 1024

/**
 * Coil's app-wide image loader (destination photos only). Every download sends [userAgent], as Wikimedia's policy
 * asks; files are kept in `cacheDir/destination_photos` (up to 50 MB, oldest dropped first). Coil 3 ignores the
 * server's cache headers by default and reads a cached file without the network, so a photo stays visible offline.
 * Sign-out keeps the cache: it holds no personal data.
 */
fun photoImageLoader(context: Context, userAgent: String): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(
                OkHttpNetworkFetcherFactory(
                    callFactory = {
                        OkHttpClient.Builder()
                            .addInterceptor { chain ->
                                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
                            }
                            .build()
                    },
                )
            )
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("destination_photos"))
                .maxSizeBytes(PHOTO_DISK_CACHE_BYTES)
                .build()
        }
        .build()
