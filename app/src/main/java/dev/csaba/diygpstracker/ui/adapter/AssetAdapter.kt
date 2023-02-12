package dev.csaba.diygpstracker.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.data.Asset
import dev.csaba.diygpstracker.data.remote.mapPeriodIntervalToProgress


class AssetAdapter(private val assetInputListener: OnAssetInputListener?) : RecyclerView.Adapter<AssetViewHolder>() {

    private val assetList = emptyList<Asset>().toMutableList()
    private var inputListener: OnAssetInputListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        inputListener = assetInputListener
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.recycler_item, parent, false
        ) as ConstraintLayout
        val trackAsset = view.findViewById<MaterialButton>(R.id.trackAsset)
        trackAsset.setOnClickListener {
            button -> assetInputListener?.run {
                this.onTrackClick(button.tag as String)
            }
        }
        return AssetViewHolder(view)
    }

    override fun getItemCount() = assetList.size

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        val asset = assetList[position]
        with(holder.containerView) {
            val assetTitle = findViewById<TextView>(R.id.assetTitle)
            assetTitle.text = asset.title
            val assetLockLat = findViewById<TextView>(R.id.assetLockLat)
            assetLockLat.text = "${asset.lockLat}"
            val assetLockLon = findViewById<TextView>(R.id.assetLockLon)
            assetLockLon.text = "${asset.lockLon}"
            val assetLockRadius = findViewById<TextView>(R.id.assetLockRadius)
            assetLockRadius.text = "${asset.lockRadius}"
            val assetLockRadiusSeekBar = findViewById<SeekBar>(R.id.assetLockRadiusSeekBar)
            assetLockRadiusSeekBar.progress = asset.lockRadius / 25
            val assetPeriodInterval = findViewById<TextView>(R.id.assetPeriodInterval)
            assetPeriodInterval.text = "${asset.periodInterval}"
            val assetPeriodIntervalSeekBar = findViewById<SeekBar>(R.id.assetPeriodIntervalSeekBar)
            assetPeriodIntervalSeekBar.progress = mapPeriodIntervalToProgress(asset.periodInterval)

            val assetTag = assetList[position].id
            val trackAsset = findViewById<MaterialButton>(R.id.trackAsset)
            trackAsset.tag = assetTag
            val flipAssetLock = findViewById<MaterialButton>(R.id.flipAssetLock)
            flipAssetLock.setIconResource(
                if (asset.lock) R.drawable.ic_lock_closed else R.drawable.ic_lock_open
            )
            val statusIcon = findViewById<ImageView>(R.id.statusIcon)
            statusIcon.setImageResource(
                if (asset.lockAlert) R.drawable.ic_warning else R.drawable.ic_check_circle
            )
        }
    }

    fun setItems(newAssetList: List<Asset>) {
        val diffResult  = DiffUtil.calculateDiff(AssetDiffUtilCallback(assetList, newAssetList))

        assetList.clear()
        assetList.addAll(newAssetList)

        diffResult.dispatchUpdatesTo(this)
    }
}

class AssetViewHolder(val containerView: View): RecyclerView.ViewHolder(containerView)

interface OnAssetInputListener {
    fun onTrackClick(assetId: String)
}
