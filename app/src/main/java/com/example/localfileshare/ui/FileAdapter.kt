package com.example.localfileshare.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.localfileshare.R
import com.example.localfileshare.data.SharedFile

class FileAdapter(
    private val fileList: MutableList<SharedFile>,
    private val onRemoveClick: (SharedFile) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileNameText: TextView = itemView.findViewById(R.id.fileNameText)
        val fileSizeText: TextView = itemView.findViewById(R.id.fileSizeText)
        val removeBtn: ImageButton = itemView.findViewById(R.id.removeFileBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = fileList[position]
        holder.fileNameText.text = file.name
        holder.fileSizeText.text = "${file.sizeFormatted} • ${file.mimeType}"

        holder.removeBtn.setOnClickListener {
            onRemoveClick(file)
        }
    }

    override fun getItemCount(): Int = fileList.size
}