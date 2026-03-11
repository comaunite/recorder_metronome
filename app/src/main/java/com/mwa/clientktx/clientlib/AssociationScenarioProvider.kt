package com.mwa.clientktx.clientlib

import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario

class AssociationScenarioProvider {

    fun provideAssociationScenario(timeoutMs: Int): LocalAssociationScenario {
        return LocalAssociationScenario(timeoutMs)
    }

}