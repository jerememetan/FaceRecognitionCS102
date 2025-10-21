def group_numbers(list, threshold):
    list_num = []
    final_list = []
    total = 0
    i = 0
    while i < len(list):
        total = 0
        list_num = []
        while total + list[i] <= threshold:
            total += list[i]
            list_num.append(list[i])
            i += 1
            if i == len(list):
                break
        final_list.append(list_num)
            
    print(final_list)
        

group_numbers([1, 3, 2, 4, 3, 2, 3, 6], 6)