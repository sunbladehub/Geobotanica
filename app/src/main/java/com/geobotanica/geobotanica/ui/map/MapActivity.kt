package com.geobotanica.geobotanica.ui.map

import android.content.Intent
import android.os.Bundle
import com.geobotanica.geobotanica.R
import com.geobotanica.geobotanica.data.entity.User
import com.geobotanica.geobotanica.data.repo.UserRepo
import com.geobotanica.geobotanica.ui.BaseActivity
import com.geobotanica.geobotanica.ui.new_plant_type.NewPlantTypeActivity
import kotlinx.android.synthetic.main.activity_map.*
import javax.inject.Inject

class MapActivity : BaseActivity() {
    @Inject lateinit var userRepo: UserRepo

    override val name = this.javaClass.name.substringAfterLast('.')

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        setSupportActionBar(toolbar)

        activityComponent.inject(this)

        fab.setOnClickListener { _ ->
            val intent = Intent(this, NewPlantTypeActivity::class.java)
                    .putExtra(getString(R.string.extra_user_id), getGuestUserId())
            startActivity(intent)
        }
    }

    private fun getGuestUserId(): Long {
        val guestUserNickname: String = "Guest"
        return if (userRepo.contains(guestUserNickname))
            userRepo.getByNickname(guestUserNickname)[0].id
        else
            userRepo.insert(User(guestUserNickname))
    }
}