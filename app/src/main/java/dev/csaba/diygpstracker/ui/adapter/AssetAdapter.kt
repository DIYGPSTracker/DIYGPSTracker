package dev.csaba.diygpstracker.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.data.Asset
import dev.csaba.diygpstracker.data.remote.mapPeriodIntervalToProgress
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.recycler_item.view.*


class AssetAdapter(private val assetInputListener: OnAssetInputListener?) : RecyclerView.Adapter<AssetViewHolder>(), SeekBar.OnSeekBarChangeListener {

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
        view.flipAssetLock.setOnClickListener {
            button -> assetInputListener?.run {
                val assetParts = (button.tag as String).split("_")
                this.onFlipAssetLockClick(assetParts[1], assetParts[0] == "l")
            }
        }
        view.deleteAsset.setOnClickListener {
            button -> assetInputListener?.run {
                this.onDeleteClick(button.tag as String)
            }
        }
        view.assetLockRadiusSeekBar.setOnSeekBarChangeListener(this)
        view.assetPeriodIntervalSeekBar.setOnSeekBarChangeListener(this)
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
            val lockAssetPrefix = if (locked) "l_" else "u_"
            flipAssetLock.tag = "${lockAssetPrefix}_${assetTag}"
            deleteAsset.tag = assetTag
            assetLockRadiusSeekBar.tag = "r_${assetTag}"
            assetPeriodIntervalSeekBar.tag = "i_${assetTag}"
        }
    }

    fun setItems(newAssetList: List<Asset>) {
        val diffResult  = DiffUtil.calculateDiff(AssetDiffUtilCallback(assetList, newAssetList))

        assetList.clear()
        assetList.addAll(newAssetList)

        diffResult.dispatchUpdatesTo(this)
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (!fromUser)
            return

        val barTag = seekBar?.tag as String
        val assetTag = barTag.substring(2)

        if (barTag[0] == 'r') {
            inputListener?.onLockRadiusChange(assetTag, seekBar.progress)
        } else if (barTag[0] == 'i') {
            inputListener?.onPeriodIntervalChange(assetTag, seekBar.progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
}

class AssetViewHolder(override val containerView: View): RecyclerView.ViewHolder(containerView), LayoutContainer

interface OnAssetInputListener {
    fun onTrackClick(assetId: String)
    fun onFlipAssetLockClick(assetId: String, lockState: Boolean)
    fun onDeleteClick(assetId: String)
    fun onLockRadiusChange(assetId: String, lockRadius: Int)
    fun onPeriodIntervalChange(assetId: String, periodIntervalProgress: Int)
}
