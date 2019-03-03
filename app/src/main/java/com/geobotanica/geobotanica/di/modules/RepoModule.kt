package com.geobotanica.geobotanica.di.modules

import android.content.Context
import com.geobotanica.geobotanica.data.GbDatabase
import com.geobotanica.geobotanica.data.dao.*
import com.geobotanica.geobotanica.data_taxa.TaxaDatabase
import com.geobotanica.geobotanica.data_taxa.dao.TaxonDao
import com.geobotanica.geobotanica.data_taxa.dao.TaxonStarDao
import com.geobotanica.geobotanica.data_taxa.dao.VernacularDao
import com.geobotanica.geobotanica.data_taxa.dao.VernacularStarDao
import com.geobotanica.geobotanica.data_taxa.repo.TaxonRepo
import com.geobotanica.geobotanica.data_taxa.repo.VernacularRepo
import com.geobotanica.geobotanica.data_taxa.util.PlantNameSearchService
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class RepoModule {
    @Provides @Singleton fun provideGbDatabase(context: Context): GbDatabase = GbDatabase.getInstance(context)
    @Provides @Singleton fun provideUserDao(gbDatabase: GbDatabase): UserDao = gbDatabase.userDao()
    @Provides @Singleton fun providePlantDao(gbDatabase: GbDatabase): PlantDao = gbDatabase.plantDao()
    @Provides @Singleton fun providePlantCompositeDao(gbDatabase: GbDatabase): PlantCompositeDao = gbDatabase.plantCompositeDao()
    @Provides @Singleton fun providePlantLocationDao(gbDatabase: GbDatabase): PlantLocationDao = gbDatabase.plantLocationDao()
    @Provides @Singleton fun providePhotoDao(gbDatabase: GbDatabase): PlantPhotoDao = gbDatabase.photoDao()
    @Provides @Singleton fun provideMeasurementDao(gbDatabase: GbDatabase): PlantMeasurementDao = gbDatabase.measurementDao()

    @Provides @Singleton fun provideTaxaDatabase(context: Context): TaxaDatabase = TaxaDatabase.getInstance(context)
    @Provides @Singleton fun provideTaxonDao(taxaDatabase: TaxaDatabase): TaxonDao = taxaDatabase.taxonDao()
    @Provides @Singleton fun provideTaxonStarDao(taxaDatabase: TaxaDatabase): TaxonStarDao = taxaDatabase.taxonStarDao()
    @Provides @Singleton fun provideVernacularDao(taxaDatabase: TaxaDatabase): VernacularDao = taxaDatabase.vernacularDao()
    @Provides @Singleton fun provideVernacularStarDao(taxaDatabase: TaxaDatabase): VernacularStarDao = taxaDatabase.vernacularStarDao()

    @Provides @Singleton fun providePlantNameSearchService(
            taxonRepo: TaxonRepo,
            vernacularRepo: VernacularRepo
    ): PlantNameSearchService = PlantNameSearchService(taxonRepo, vernacularRepo)
}
