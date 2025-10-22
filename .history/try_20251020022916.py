def group_numbers(list, threshold):
    ascList = list.sort()
    desList = list.sort(reverse=True)
    list_num = []
    final_list = []
    total = 0
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
        

group_numbers([1, 3, 4, 3, 5, 2, 5, 6], 6)