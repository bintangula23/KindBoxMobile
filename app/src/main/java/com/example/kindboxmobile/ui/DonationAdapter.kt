package com.example.kindboxmobile.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.kindboxmobile.R
import com.example.kindboxmobile.data.DonationEntity
import com.example.kindboxmobile.databinding.ItemDonationBinding

class DonationAdapter(private val onItemClick: (DonationEntity) -> Unit) :
    ListAdapter<DonationEntity, DonationAdapter.DonationViewHolder>(DIFF_CALLBACK) {

    inner class DonationViewHolder(private val binding: ItemDonationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(donation: DonationEntity) {
            binding.tvItemTitle.text = donation.title

            // TAMPILKAN JUMLAH & SISA
            val total = donation.quantity
            val interested = donation.interestedCount
            val remaining = if (total - interested < 0) 0 else total - interested

            binding.tvItemQuantity.text = "Stok: $remaining / $total"
            binding.tvItemLocation.text = if (donation.location.isNotEmpty()) donation.location else "Lokasi -"

            if (donation.imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(donation.imageUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    // FIX: Bypass cache Glide agar gambar yang baru diupload muncul
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .centerCrop()
                    .into(binding.ivItemImage)
            } else {
                binding.ivItemImage.setImageResource(R.drawable.ic_launcher_foreground)
            }

            itemView.setOnClickListener { onItemClick(donation) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DonationViewHolder {
        val binding = ItemDonationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DonationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DonationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DonationEntity>() {
            override fun areItemsTheSame(oldItem: DonationEntity, newItem: DonationEntity): Boolean =
                oldItem.id == newItem.id

            // FIX SYNTAX ERROR: Perbaikan kesalahan pengetikan sebelumnya
            override fun areContentsTheSame(oldItem: DonationEntity, newItem: DonationEntity): Boolean =
                oldItem == newItem
        }
    }
}