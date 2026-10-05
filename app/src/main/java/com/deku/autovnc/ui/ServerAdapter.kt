package com.deku.autovnc.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.deku.autovnc.databinding.ItemServerBinding
import com.deku.autovnc.model.VncServerProfile

class ServerAdapter(
    private var servers: MutableList<VncServerProfile>,
    private val onConnectClick: (VncServerProfile) -> Unit,
    private val onEditClick: (VncServerProfile) -> Unit,
    private val onDeleteClick: (VncServerProfile) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemServerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemServerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val server = servers[position]
        holder.binding.tvServerName.text = server.name
        holder.binding.tvServerAddress.text = "${server.host}:${server.port}"

        // Bind FPS, Bitrate/ColorDepth, and Scale badges
        val fpsVal = if (server.fps > 0) server.fps else 30
        val depthVal = if (server.colorDepth > 0) server.colorDepth else 32
        holder.binding.tvFpsBadge.text = "⚡ $fpsVal FPS"
        holder.binding.tvBitrateBadge.text = if (depthVal == 16) "🎨 16-bit (%50 Tasarruf)" else "🎨 32-bit (Kayıpsız)"
        holder.binding.tvScaleBadge.text = "📐 ${server.scaleMode.name}"

        holder.binding.btnConnect.setOnClickListener {
            onConnectClick(server)
        }

        holder.binding.btnEdit.setOnClickListener {
            onEditClick(server)
        }

        holder.binding.btnDelete.setOnClickListener {
            onDeleteClick(server)
        }
    }

    override fun getItemCount(): Int = servers.size

    fun updateList(newList: List<VncServerProfile>) {
        servers.clear()
        servers.addAll(newList)
        notifyDataSetChanged()
    }
}
