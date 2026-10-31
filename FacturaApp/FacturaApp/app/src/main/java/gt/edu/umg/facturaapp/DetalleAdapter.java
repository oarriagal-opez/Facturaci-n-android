package gt.edu.umg.facturaapp;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class DetalleAdapter extends RecyclerView.Adapter<DetalleAdapter.VH> {

    private final List<DetalleFactura> data;
    private int seleccionada = -1;

    public DetalleAdapter(List<DetalleFactura> data) {
        this.data = data;
    }

    public int getSeleccionada() { return seleccionada; }

    public void limpiarSeleccion() { seleccionada = -1; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_detalle, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DetalleFactura d = data.get(position);
        h.id.setText(String.valueOf(d.producto.idProducto));
        h.nombre.setText(d.producto.nombre);
        h.cant.setText(String.valueOf(d.cantidad));
        h.precio.setText(String.format(Locale.US, "%.2f", d.precioUnitario));
        h.sub.setText(String.format(Locale.US, "%.2f", d.calcularSubtotal()));
        h.itemView.setBackgroundColor(position == seleccionada ? Color.parseColor("#BBDEFB") : Color.TRANSPARENT);
        h.itemView.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            int anterior = seleccionada;
            seleccionada = pos;
            if (anterior >= 0) notifyItemChanged(anterior);
            notifyItemChanged(pos);
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView id, nombre, cant, precio, sub;
        VH(@NonNull View v) {
            super(v);
            id = v.findViewById(R.id.tvItemId);
            nombre = v.findViewById(R.id.tvItemNombre);
            cant = v.findViewById(R.id.tvItemCant);
            precio = v.findViewById(R.id.tvItemPrecio);
            sub = v.findViewById(R.id.tvItemSub);
        }
    }
}
