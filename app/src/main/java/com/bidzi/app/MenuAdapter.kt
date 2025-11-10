package com.bidzi.app
// Adapter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bidzi.app.databinding.ItemProfileMenuBinding

class MenuAdapter(
    private val menuItems: List<MenuItem>,
    private val onMenuItemClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    inner class MenuViewHolder(private val binding: ItemProfileMenuBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(menuItem: MenuItem) {
            binding.apply {
                tvMenuTitle.text = menuItem.title
                ivMenuIcon.setImageResource(menuItem.icon)

                // Set background color based on menu item type
                when (menuItem.type) {
                    MenuItemType.LOGOUT -> {
                        root.setBackgroundColor(root.context.getColor(R.color.logout_bg))
                        tvMenuTitle.setTextColor(root.context.getColor(R.color.red))
                        ivMenuArrow.setColorFilter(
                            root.context.getColor(R.color.red),
                            android.graphics.PorterDuff.Mode.SRC_IN
                        )
                    }
                    else -> {
                        root.setBackgroundColor(root.context.getColor(R.color.white))
                        tvMenuTitle.setTextColor(root.context.getColor(R.color.dark_gray))
                        ivMenuArrow.setColorFilter(
                            root.context.getColor(R.color.medium_gray),
                            android.graphics.PorterDuff.Mode.SRC_IN
                        )
                    }
                }

                root.setOnClickListener {
                    onMenuItemClick(menuItem)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val binding = ItemProfileMenuBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MenuViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.bind(menuItems[position])
    }

    override fun getItemCount(): Int = menuItems.size
}