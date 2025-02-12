package com.codingblocks.cbonlineapp.course

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.SnapHelper
import com.codingblocks.cbonlineapp.BuildConfig
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.commons.OnCartItemClickListener
import com.codingblocks.cbonlineapp.course.batches.BatchesAdapter
import com.codingblocks.cbonlineapp.database.models.InstructorModel
import com.codingblocks.cbonlineapp.insturctors.InstructorDataAdapter
import com.codingblocks.cbonlineapp.util.Components
import com.codingblocks.cbonlineapp.util.MediaUtils.getYotubeVideoId
import com.codingblocks.cbonlineapp.util.extensions.loadImage
import com.codingblocks.cbonlineapp.util.extensions.observeOnce
import com.codingblocks.cbonlineapp.util.extensions.observer
import com.codingblocks.onlineapi.models.Course
import com.codingblocks.onlineapi.models.Sections
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.youtube.player.YouTubeInitializationResult
import com.google.android.youtube.player.YouTubePlayer
import com.google.android.youtube.player.YouTubePlayerSupportFragment
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import kotlinx.android.synthetic.main.activity_course.*
import kotlinx.android.synthetic.main.bottom_cart_sheet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.toast
import org.koin.androidx.viewmodel.ext.android.viewModel

class CourseActivity : AppCompatActivity(), AnkoLogger {
    lateinit var courseId: String
    lateinit var courseName: String
    private lateinit var progressBar: Array<ProgressBar?>
    private lateinit var batchAdapter: BatchesAdapter
    private lateinit var instructorAdapter: InstructorDataAdapter
    private lateinit var youtubePlayerInit: YouTubePlayer.OnInitializedListener
    private val batchSnapHelper: SnapHelper = LinearSnapHelper()
    private val sectionAdapter = SectionsDataAdapter(ArrayList())

    private val viewModel by viewModel<CourseViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course)
        courseId = intent.getStringExtra("courseId")
        courseName = intent.getStringExtra("courseName") ?: ""
        val image = intent.getStringExtra("courseLogo") ?: ""
        setImageAndTitle(image, courseName)

        init()
    }

    private fun setImageAndTitle(image: String, courseName: String) {
        coursePageLogo.loadImage(image)
        title = courseName
        coursePageTitle.text = courseName
    }

    private fun init() {
        progressBar = arrayOf(
            courseProgress1,
            courseProgress2,
            courseProgress3,
            courseProgress4,
            courseProgress5
        )
        setSupportActionBar(toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        viewModel.sheetBehavior = BottomSheetBehavior.from(bottom_sheet)
        viewModel.sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN

        fetchCourse()
    }

    private fun showBottomSheet(newId: String, newName: String) {
        viewModel.image.observer(this) {
            newImage.loadImage(it ?: "")
        }
        viewModel.name.observer(this) {
            oldTitle.text = it
            newTitle.text = newName
        }
        checkoutBtn.setOnClickListener {
            viewModel.sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
            Components.openChrome(this, "https://dukaan.codingblocks.com/mycart")
        }
        continueBtn.setOnClickListener {
            viewModel.sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
            viewModel.clearCartProgress.observeOnce {
                if (it) addToCart(newId, newName)
                else toast("Error in Clearing Cart, please try again")
            }
            viewModel.clearCart()
        }
        viewModel.getCart()
    }

    private fun fetchInstructors(id: String) {
        instructorAdapter = InstructorDataAdapter(ArrayList())

        instructorRv.layoutManager = LinearLayoutManager(this)
        instructorRv.adapter = instructorAdapter

        viewModel.getInstructors(id)
            .observer(this) {
                if (!it.isNullOrEmpty()) {
                    instructorAdapter.setData(it as ArrayList<InstructorModel>)
                    var instructors = "Mentors: "
                    for (i in it.indices) {
                        if (i == 0) {
                            instructors += it[i].name
                        } else if (i == 1) {
                            instructors += ", ${it[i].name}"
                        } else if (i >= 2) {
                            instructors += "+ " + (it.size - 2) + " more"
                            break
                        }
                        coursePageMentors.text = instructors
                    }
                }
            }
    }

    private fun fetchCourse() {

        viewModel.fetchedCourse
            .observer(this) { course ->
                fetchInstructors(course.id)
                viewModel.getCourseFeatures(courseId).observer(this) {
                    if (it.isNotEmpty())
                        it.forEachIndexed { index, courseFeatures ->
                            when (index) {
                                0 -> {
                                    feature_icon_1.loadImage(courseFeatures.icon, scale = true)
                                    features_text_1.text = courseFeatures.text
                                }
                                1 -> {
                                    feature_icon_2.loadImage(courseFeatures.icon, scale = true)
                                    features_text_2.text = courseFeatures.text
                                }
                                2 -> {
                                    feature_icon_3.loadImage(courseFeatures.icon, scale = true)
                                    features_text_3.text = courseFeatures.text
                                }
                                3 -> {
                                    feature_icon_4.loadImage(courseFeatures.icon, scale = true)
                                    features_text_4.text = courseFeatures.text
                                }
                            }
                        }
                }

                batchAdapter = BatchesAdapter(ArrayList(), object : OnCartItemClickListener {
                    override fun onItemClick(id: String, name: String) {
                        addToCart(id, name)
                    }
                })
                batchRv.layoutManager =
                    LinearLayoutManager(this@CourseActivity, LinearLayoutManager.HORIZONTAL, false)
                batchRv.adapter = batchAdapter
                batchSnapHelper.attachToRecyclerView(batchRv)
                setImageAndTitle(course.logo, course.title)
                coursePageSubtitle.text = course.subtitle
                if (course.faq.isNullOrEmpty()) {
                    faqMarkdown.isVisible = false
                    faqTitleTv.isVisible = false
                    faqView.isVisible = false
                } else {
                    faqMarkdown.loadMarkdown(course.faq)
                }
                coursePageSummary.loadMarkdown(course.summary)
                trialBtn.setOnClickListener {
                    if (course.runs != null) {
                        viewModel.enrollTrialProgress.observeOnce {
                            Components.showConfirmation(this@CourseActivity, "trial")
                        }
                        viewModel.enrollTrial(course.runs?.get(0)?.id ?: "")
                    } else {
                        toast("No available runs right now ! Please check back later")
                    }
                }
                course.runs?.let { batchAdapter.setData(it) }
                buyBtn.setOnClickListener {
                    if (course.runs != null) {
                        focusOnView(scrollView, batchRv)
                    } else {
                        toast("No available runs right now ! Please check back later")
                    }
                }
                fetchTags(course)
                showPromoVideo(course.promoVideo)
                fetchRating(course.id)
                if (!course.runs.isNullOrEmpty()) {
                    val sections = course.runs?.get(0)?.sections
                    val sectionsList = ArrayList<Sections>()
                    rvExpendableView.layoutManager = LinearLayoutManager(this@CourseActivity)
                    rvExpendableView.adapter = sectionAdapter
                    runOnUiThread {
                        sections?.forEachIndexed { index, section ->
                            GlobalScope.launch(Dispatchers.IO) {
                                val response2 = viewModel.getCourseSection(section.id)
                                if (response2.isSuccessful) {
                                    val value = response2.body()
                                    value?.order = index
                                    if (value != null) {
                                        sectionsList.add(value)
                                    }
                                    if (sectionsList.size == sections.size) {
                                        sectionsList.sortBy { it.order }
                                        runOnUiThread {
                                            sectionAdapter.setData(sectionsList)
                                        }
                                    }
                                } else {
                                    toast("Error ${response2.code()}")
                                }
                            }
                        }
                    }
                }
            }
        viewModel.getCourse(courseId)
    }

    private fun fetchTags(course: Course) {
        course.runs?.forEach { singleCourse ->
            if (singleCourse.tags?.size == 0) {
                tagstv.visibility = View.GONE
                coursePagevtags.visibility = View.GONE
                tagsChipgroup.visibility = View.GONE
            } else {
                tagstv.visibility = View.VISIBLE
                coursePagevtags.visibility = View.VISIBLE
                tagsChipgroup.visibility = View.VISIBLE

                singleCourse.tags?.forEach {
                    val chip = Chip(this)
                    chip.text = it.name
                    val typeFace: Typeface? =
                        ResourcesCompat.getFont(this.applicationContext, R.font.nunitosans_regular)
                    chip.typeface = typeFace
                    tagsChipgroup.addView(chip)
                }
            }
        }
    }

    private fun addToCart(id: String, name: String) {
        viewModel.addedToCartProgress.observeOnce {
            if (it) {
                Components.openChrome(this, "https://dukaan.codingblocks.com/mycart")
            } else {
                showBottomSheet(id, name)
            }
        }
        viewModel.addToCart(id)
    }

    private fun showPromoVideo(promoVideo: String) {
        youtubePlayerInit = object : YouTubePlayer.OnInitializedListener {
            override fun onInitializationFailure(
                p0: YouTubePlayer.Provider?,
                p1: YouTubeInitializationResult?
            ) {
            }

            override fun onInitializationSuccess(
                p0: YouTubePlayer.Provider?,
                youtubePlayerInstance: YouTubePlayer?,
                p2: Boolean
            ) {
                if (!p2) {
                    youtubePlayerInstance?.cueVideo(getYotubeVideoId(promoVideo))
                }
            }
        }
        val youTubePlayerSupportFragment =
            supportFragmentManager.findFragmentById(R.id.displayYoutubeVideo) as YouTubePlayerSupportFragment?
        youTubePlayerSupportFragment?.initialize(BuildConfig.YOUTUBE_KEY, youtubePlayerInit)
    }

    private fun fetchRating(id: String) {
        viewModel.getCourseRating(id).observeOnce {
            it.apply {
                coursePageRatingCountTv.text = "$count Rating"
                coursePageRatingTv.text = "$rating out of 5 stars"
                coursePageRatingBar.rating = rating.toFloat()
                for (i in 0 until progressBar.size) {
                    progressBar[i]?.max = it.count * 1000
                    progressBar[i]?.progress = it.stats[i].toInt() * 1000

                    // Todo Add Animation on Focus
                    //                    val anim =
                    //                        ProgressBarAnimation(progressBar[i], 0F, it.stats[i].toInt() * 1000F)
                    //                    anim.duration = 1500
                    //                    progressBar[i]?.startAnimation(anim)
                }
            }
        }
        viewModel.getCourseRating(id)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase))
    }

    private fun focusOnView(scroll: NestedScrollView, view: View) {
        Handler().post {
            val vTop = view.top
            val vBottom = view.bottom
            val sWidth = scroll.width
            scroll.smoothScrollTo(0, (vTop + vBottom - sWidth) / 2)
        }
    }

    override fun onBackPressed() {
        if (viewModel.sheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED) {
            viewModel.sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        } else {
            super.onBackPressed()
        }
    }
}
