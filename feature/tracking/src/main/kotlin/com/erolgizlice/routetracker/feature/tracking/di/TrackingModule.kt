package com.erolgizlice.routetracker.feature.tracking.di

import com.erolgizlice.routetracker.feature.tracking.TrackingViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val trackingModule = module {
    viewModelOf(::TrackingViewModel)
}
