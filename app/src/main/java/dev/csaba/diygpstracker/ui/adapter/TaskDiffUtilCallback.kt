package dev.csaba.diygpstracker.ui.adapter

import androidx.recyclerview.widget.DiffUtil
import dev.csaba.diygpstracker.data.Asset


class AssetDiffUtilCallback(
    private val oldList: List<Asset>,
    private val newList: List<Asset>
): DiffUtil.Callback() {

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) = oldList[oldItemPosition].id == newList[newItemPosition].id

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].title == newList[newItemPosition].title &&
                oldList[oldItemPosition].lock == newList[newItemPosition].lock &&
                oldList[oldItemPosition].lockLat == newList[newItemPosition].lockLat &&
                oldList[oldItemPosition].lockLon == newList[newItemPosition].lockLon &&
                oldList[oldItemPosition].lockRadius == newList[newItemPosition].lockRadius &&
                oldList[oldItemPosition].lockAlert == newList[newItemPosition].lockAlert &&
                oldList[oldItemPosition].periodInterval == newList[newItemPosition].periodInterval
    }
}
