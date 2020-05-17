package charles.com.avsample

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import charles.com.avsample.photo.PhotoDemoActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recordDemo.setOnClickListener {
            startActivity(Intent(this, PhotoDemoActivity::class.java))
        }
    }
}
