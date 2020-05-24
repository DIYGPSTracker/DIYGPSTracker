package dev.csaba.diygpstracker.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.data.Asset
import dev.csaba.diygpstracker.data.remote.mapPeriodIntervalToProgress
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.recycler_item.view.*


class AssetAdapter(private val assetInputListener: OnAssetInputListener?) : RecyclerView.Adapter<AssetViewHolder>() {

    private val assetList = emptyList<Asset>().toMutableList()
    private var inputListener: OnAssetInputListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        inputListener = assetInputListener
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.recycler_item, parent, false
        )
        view.trackAsset.setOnClickListener {
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
            assetTitle.text = asset.title
            assetLockLat.text = "${asset.lockLat}"
            assetLockLon.text = "${asset.lockLon}"
            assetLockRadius.text = "${asset.lockRadius}"
            assetLockRadiusSeekBar.progress = asset.lockRadius / 25
            assetPeriodInterval.text = "${asset.periodInterval}"
            assetPeriodIntervalSeekBar.progress = mapPeriodIntervalToProgress(asset.periodInterval)

            val assetTag = assetList[position].id
            trackAsset.tag = assetTag
            val locked = Math.abs(asset.lockLat) > 1e-6 && Math.abs(asset.lockLon) > 1e-6
            flipAssetLock.setIconResource(
                if (locked) R.drawable.ic_lock_closed else R.drawable.ic_lock_open
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

class AssetViewHolder(override val containerView: View): RecyclerView.ViewHolder(containerView), LayoutContainer

interface OnAssetInputListener {
    fun onTrackClick(assetId: String)
}
