package com.github.ljarka.picfort.ui.family

import com.github.ljarka.picfort.core.model.FamilyMemberProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class FamilyModule {
    @Binds
    abstract fun backend(api: FamilyApi): FamilyBackend

    @Binds
    abstract fun offlineCopy(file: FileFamilyOfflineCopy): FamilyOfflineCopy

    @Binds
    abstract fun peopleSource(api: PeopleApi): PeopleSource

    @Binds
    abstract fun familyMemberProvider(repo: FamilyRepository): FamilyMemberProvider
}
