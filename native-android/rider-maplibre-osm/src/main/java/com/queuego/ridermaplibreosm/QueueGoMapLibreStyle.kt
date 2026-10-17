package com.queuego.ridermaplibreosm

object QueueGoMapLibreStyle {
    fun rasterFallbackJson(): String = """
        {
          "version": 8,
          "sources": {
            "queuego_osm": {
              "type": "raster",
              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "attribution": "© OpenStreetMap contributors"
            }
          },
          "layers": [
            {
              "id": "queuego-background",
              "type": "background",
              "paint": {"background-color": "#f3f0e8"}
            },
            {
              "id": "queuego-osm",
              "type": "raster",
              "source": "queuego_osm"
            }
          ]
        }
    """.trimIndent()
}
