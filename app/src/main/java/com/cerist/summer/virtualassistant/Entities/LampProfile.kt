package com.cerist.summer.virtualassistant.Entities

import java.util.*

class LampProfile{

    companion object {
         const val DEVICE_MAC_ADDRESS:String = "EF:F1:F9:DB:E5:3E"


        const val LUMINOSITY_CHARACTERISTIC_UUID =  "0000beef-1212-EFDE-1523-785FEABCD123"
        const val STATE_CHARACTERISTIC_UUID =  "0000beef-1212-EFDE-1523-785FEABCD123"
    }

    enum class State(val value: Int) {
        ON(1),
        OFF(0)
    }

    enum class Luminosity(val value:Int) {
        NON(0),
        LOW(1),
        MEDIUM(2),
        HIGH(3),
        MAX(4)
    }




}