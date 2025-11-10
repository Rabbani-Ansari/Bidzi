package com.bidzi.app


import android.content.Context

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer

/**
 * Custom cluster renderer for vehicle markers
 * Use this when you have many vehicles (50+) on the map
 *
 * Add dependency: implementation("com.google.maps.android:android-maps-utils:3.8.0")
 */
class VehicleClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<VehicleClusterItem>
) : DefaultClusterRenderer<VehicleClusterItem>(context, map, clusterManager) {

    override fun onBeforeClusterItemRendered(
        item: VehicleClusterItem,
        markerOptions: MarkerOptions
    ) {
        super.onBeforeClusterItemRendered(item, markerOptions)

        // Customize marker based on vehicle type
        val icon = when (item.vehicle.type.lowercase()) {
            "auto" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_auto_marker)
            "bike" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_bike_marker)
            "car" -> BitmapDescriptorFactory.fromResource(R.drawable.ic_car_marker)
            else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
        }

        markerOptions.apply {
            icon(icon)
            title(item.vehicle.type)
            snippet("${item.vehicle.driverName} - ${item.vehicle.rating}⭐")
            anchor(0.5f, 0.5f)
        }
    }
}

/**
 * Cluster item wrapper for NearbyVehicle
 */
data class VehicleClusterItem(
    val vehicle: NearbyVehicle
) : com.google.maps.android.clustering.ClusterItem {

    override fun getPosition() = com.google.android.gms.maps.model.LatLng(
        vehicle.latitude,
        vehicle.longitude
    )

    override fun getTitle() = vehicle.type

    override fun getSnippet() = "${vehicle.driverName} - ${vehicle.rating}⭐"

    override fun getZIndex() = 0f
}

/**
 * Extension function to use clustering in HomeFragment
 */
fun setupClusterManager(
    context: Context,
    map: GoogleMap,
    vehicles: List<NearbyVehicle>
): ClusterManager<VehicleClusterItem> {

    val clusterManager = ClusterManager<VehicleClusterItem>(context, map)
    clusterManager.renderer = VehicleClusterRenderer(context, map, clusterManager)

    // Add all vehicles to cluster
    vehicles.forEach { vehicle ->
        clusterManager.addItem(VehicleClusterItem(vehicle))
    }

    // Set up cluster manager as map click listeners
    map.setOnCameraIdleListener(clusterManager)
    map.setOnMarkerClickListener(clusterManager)

    return clusterManager
}