import 'package:flutter/material.dart';

class RelationDropdown<T> extends StatelessWidget {
  final String label;
  final T? value;
  final List<T> items;
  final String Function(T) itemLabel;
  final dynamic Function(T) itemValue;
  final ValueChanged<T?> onChanged;
  final bool isRequired;

  const RelationDropdown({
    super.key,
    required this.label,
    required this.value,
    required this.items,
    required this.itemLabel,
    required this.itemValue,
    required this.onChanged,
    this.isRequired = false,
  });

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<T>(
      initialValue: value,
      decoration: InputDecoration(
        labelText: isRequired ? '$label *' : label,
      ),
      isExpanded: true,
      items: [
        if (!isRequired)
          DropdownMenuItem<T>(
            value: null,
            child: const Text('Ninguno', style: TextStyle(color: Colors.grey)),
          ),
        ...items.map((item) => DropdownMenuItem<T>(
              value: item,
              child: Text(itemLabel(item), overflow: TextOverflow.ellipsis),
            )),
      ],
      onChanged: onChanged,
      validator: isRequired
          ? (val) => val == null ? 'Selecciona una opción' : null
          : null,
    );
  }
}

class RelationMultiSelectDialog<T> extends StatefulWidget {
  final String title;
  final List<T> allItems;
  final List<T> selectedItems;
  final String Function(T) itemLabel;

  const RelationMultiSelectDialog({
    super.key,
    required this.title,
    required this.allItems,
    required this.selectedItems,
    required this.itemLabel,
  });

  @override
  State<RelationMultiSelectDialog<T>> createState() => _RelationMultiSelectDialogState<T>();
}

class _RelationMultiSelectDialogState<T> extends State<RelationMultiSelectDialog<T>> {
  late List<T> _tempSelected;

  @override
  void initState() {
    super.initState();
    _tempSelected = List<T>.from(widget.selectedItems);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: SizedBox(
        width: double.maxFinite,
        child: widget.allItems.isEmpty
            ? const Padding(
                padding: EdgeInsets.all(16.0),
                child: Text('No hay elementos disponibles.'),
              )
            : ListView.builder(
                shrinkWrap: true,
                itemCount: widget.allItems.length,
                itemBuilder: (ctx, i) {
                  final item = widget.allItems[i];
                  final isChecked = _tempSelected.contains(item);
                  return CheckboxListTile(
                    title: Text(widget.itemLabel(item)),
                    value: isChecked,
                    onChanged: (val) {
                      setState(() {
                        if (val == true) {
                          _tempSelected.add(item);
                        } else {
                          _tempSelected.remove(item);
                        }
                      });
                    },
                  );
                },
              ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancelar'),
        ),
        ElevatedButton(
          onPressed: () => Navigator.of(context).pop(_tempSelected),
          child: const Text('Aceptar'),
        ),
      ],
    );
  }
}
