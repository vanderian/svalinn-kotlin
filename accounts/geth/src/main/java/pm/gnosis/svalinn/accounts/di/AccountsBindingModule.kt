package pm.gnosis.svalinn.accounts.di

import dagger.Binds
import dagger.Module
import pm.gnosis.svalinn.accounts.base.repositories.AccountsRepository
import pm.gnosis.svalinn.accounts.repositories.impls.GethAccountsRepository

@Module
abstract class AccountsBindingModule {
    @Binds
    abstract fun bindAccountsRepository(gethAccountsRepository: GethAccountsRepository): AccountsRepository
}
